package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.client.sync.mapper.toEntity
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.application.outbound.LolApiClientPort
import io.olkkani.lolviewback.application.sync.MatchSyncWindowChecker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PollMatchDataService(
    private val tournamentRepository: TournamentRepository,
    private val matchRepository: MatchRepository,
    private val apiClientPort: LolApiClientPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun syncUpcomingMatches() {
        val today = LocalDate.now()
        val allTournaments = tournamentRepository.findAll()
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
