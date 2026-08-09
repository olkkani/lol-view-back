package io.olkkani.lolviewback.infastructure.inbound.scheduler

import io.olkkani.lolviewback.infastructure.outbound.client.sync.LolEsportsApiClient
import io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper.toEntity
import io.olkkani.lolviewback.infastructure.outbound.repository.MatchRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.TournamentRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class MatchDailySyncScheduler(
    private val tournamentRepository: TournamentRepository,
    private val matchRepository: MatchRepository,
    private val apiClient: LolEsportsApiClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 5 0 * * *")
    fun syncUpcomingMatches() {
        val today = LocalDate.now()
        val allTournaments = tournamentRepository.findAll()
        val triggeringTournaments = allTournaments.filter { tournament ->
            val windowStart = tournament.startDate.minusDays(7)
            !today.isBefore(windowStart) && !today.isAfter(tournament.endDate)
        }

        val byLeague = triggeringTournaments.groupBy { it.league.id }
        for ((_, tournamentsForLeague) in byLeague) {
            if (tournamentsForLeague.size > 1) {
                log.warn(
                    "League {} has {} concurrently-triggering tournaments — premise violated, skipping",
                    tournamentsForLeague.first().league.leagueApiId,
                    tournamentsForLeague.size,
                )
                continue
            }

            val tournament = tournamentsForLeague.first()
            try {
                val fetched = runBlocking { apiClient.fetchMatchesForLeague(tournament.league.leagueApiId) }
                for (apiResponse in fetched) {
                    if (matchRepository.findByMatchApiId(apiResponse.apiId) != null) continue
                    matchRepository.save(apiResponse.toEntity(tournament))
                }
            } catch (e: Exception) {
                log.error("Failed to sync matches for tournament {}", tournament.tournamentApiId, e)
            }
        }
    }
}
