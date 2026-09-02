package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchApiState
import io.olkkani.lolviewback.adapter.outbound.client.sync.mapper.toEntity
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.TournamentPollingDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.application.outbound.LolApiClientPort
import io.olkkani.lolviewback.application.sync.MatchSyncWindowChecker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.YamlProcessor
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PollMatchDataService(
    private val tournamentRepository: TournamentRepository,
    private val tournamentPollingDao: TournamentPollingDao,
    private val matchRepository: MatchRepository,
    private val apiClientPort: LolApiClientPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun syncUpcomingMatches() {
        val today = LocalDate.now()
        val allTournaments = tournamentRepository.findAll()

        val inProgressTournaments = tournamentPollingDao.findInProgressTournaments()

        val selections = MatchSyncWindowChecker.selectTournamentsToSync(allTournaments, today)

        for (selection in selections) {
            val tournament = selection.tournament
            if (tournament == null) {
                val skipped = selection.skippedDueToMultipleTriggers
                log.warn(
                    "League {} has {} concurrently-triggering tournaments — premise violated, skipping",
                    skipped.first().league.leagueApiId,
                    skipped.size,
                )
                continue
            }

            syncMatchesForTournament(tournament)
        }











        for (inProgressTournament in inProgressTournaments) {
            val unstartedMatches = apiClientPort.fetchMatches(inProgressTournament.league.leagueApiId)
                .filter { it.state == MatchApiState.UNSTARTED.toString()}





            for(unstartedMatch in unstartedMatches){
                unstartedMatch.match
            }


            // save match

            // save matchParticipant
        }


    }

    private suspend fun syncMatchesForTournament(tournament: Tournament) {
        try {
            val fetched = apiClientPort.fetchMatchesForLeague(tournament.league.leagueApiId)
            for (apiResponse in fetched) {
                if (matchRepository.findByMatchApiId(apiResponse.apiId) != null) continue
                matchRepository.save(apiResponse.toEntity(tournament))
            }
        } catch (e: Exception) {
            log.error("Failed to sync matches for tournament {}", tournament.tournamentApiId, e)
        }
    }
}

