package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.SetState
import io.olkkani.lolviewback.adapter.outbound.persistence.ClubRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchSetDao
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchSetRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchSet
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType
import io.olkkani.lolviewback.adapter.outbound.persistence.projection.MatchSetProjection
import io.olkkani.lolviewback.application.outbound.LolApiClientPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PollMatchSetService(
    private val apiClientPort: LolApiClientPort,
    private val matchRepository: MatchRepository,
    private val matchSetRepository: MatchSetRepository,
    private val clubRepository: ClubRepository,
    private val matchSetDao: MatchSetDao,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun syncMatchSets() {
        val inProgressMatches = withContext(Dispatchers.IO) {
            matchRepository.findAllByMatchState(MatchState.IN_PROGRESS)
        }
        val matchDates = inProgressMatches.associate { it.id to it.startTime.toLocalDate() }
        val savedMatchSetsByMatchId: Map<Long, List<MatchSetProjection>> =
            matchSetDao.findWinCountsByMatchIdIn(inProgressMatches.map { it.id }, matchDates)
                .groupBy { it.matchId }

        for (match in inProgressMatches) {
            try {
                syncMatchSet(match, savedMatchSetsByMatchId[match.id].orEmpty())
            } catch (e: Exception) {
                log.error("Failed to sync match sets for match {}", match.matchApiId, e)
            }
        }
    }

    private suspend fun syncMatchSet(
        match: Match,
        savedMatchSets: List<MatchSetProjection>,
    ) {
        val apiInProgressSetResponse = apiClientPort.fetchMatchSet(match.matchApiId) ?: return
        val apiInProgressMatchData = apiInProgressSetResponse.match
        val apiMatchTeams = apiInProgressMatchData.teams
        val apiMatchSets = apiInProgressMatchData.games

        val savedCompletedGameCount = savedMatchSets.sumOf { it.wins }
        val newlyCompletedGames = apiMatchSets
            .filter { it.state == SetState.COMPLETED }
            .sortedBy { it.number }
            .drop(savedCompletedGameCount)

        if (newlyCompletedGames.isEmpty()) return

        val apiTeamA = apiMatchTeams.first()
        val apiTeamB = apiMatchTeams.last()
        val savedTeamA = savedMatchSets.find { it.abbreviation == apiTeamA.code }
            ?: error("No saved club found for team ${apiTeamA.code} on match ${match.matchApiId}")
        val savedTeamB = savedMatchSets.find { it.abbreviation == apiTeamB.code }
            ?: error("No saved club found for team ${apiTeamB.code} on match ${match.matchApiId}")

        val matchRef = withContext(Dispatchers.IO) {
            matchRepository.getReferenceById(match.id)
        }

        // Distribute each team's newly-gained wins across the newly completed games,
        // in order, since the API only reports final win totals per team, not per game.
        var teamAWinsRemaining = apiTeamA.result.gameWins - savedTeamA.wins
        var teamBWinsRemaining = apiTeamB.result.gameWins - savedTeamB.wins

        for (apiCompletedGame in newlyCompletedGames) {
            val winningClubId = when {
                teamAWinsRemaining > 0 -> {
                    teamAWinsRemaining--
                    savedTeamA.clubId
                }
                teamBWinsRemaining > 0 -> {
                    teamBWinsRemaining--
                    savedTeamB.clubId
                }
                else -> error(
                    "Could not determine winning team for completed game ${apiCompletedGame.id} on match ${match.matchApiId}",
                )
            }

            val clubRef = withContext(Dispatchers.IO) {
                clubRepository.getReferenceById(winningClubId)
            }

            matchSetRepository.save(
                MatchSet(
                    setApiId = apiCompletedGame.id,
                    setNumber = apiCompletedGame.number,
                    club = clubRef,
                    match = matchRef,
                ),
            )
        }

        val winsToClinch = winsToClinchSeries(match.matchType)
        if (apiTeamA.result.gameWins >= winsToClinch || apiTeamB.result.gameWins >= winsToClinch) {
            matchRef.matchState = MatchState.COMPLETED
            withContext(Dispatchers.IO) {
                matchRepository.save(matchRef)
            }
        }
    }

    private fun winsToClinchSeries(matchType: MatchType): Int = when (matchType) {
        MatchType.BO3 -> 2
        MatchType.BO5 -> 3
    }
}