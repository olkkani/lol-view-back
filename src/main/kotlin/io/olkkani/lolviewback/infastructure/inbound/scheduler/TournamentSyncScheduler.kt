package io.olkkani.lolviewback.infastructure.inbound.scheduler

import io.olkkani.lolviewback.domain.sync.TournamentDueChecker
import io.olkkani.lolviewback.infastructure.outbound.client.sync.LolEsportsApiClient
import io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper.toEntity
import io.olkkani.lolviewback.infastructure.outbound.repository.LeagueRecurrenceWindowRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.LeagueRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.TournamentRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class TournamentSyncScheduler(
    private val leagueRepository: LeagueRepository,
    private val windowRepository: LeagueRecurrenceWindowRepository,
    private val tournamentRepository: TournamentRepository,
    private val apiClient: LolEsportsApiClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *")
    fun syncDueTournaments() {
        val today = LocalDate.now()
        val activeLeagues = leagueRepository.findByIsActiveTrue()

        for (league in activeLeagues) {
            val windows = windowRepository.findByLeague(league)
            val existingTournaments = tournamentRepository.findAll().filter { it.league.id == league.id }

            if (TournamentDueChecker.shouldDeactivate(windows, existingTournaments, today)) {
                league.isActive = false
                leagueRepository.save(league)
                log.info("Deactivated league {} — no new tournament found within grace period", league.leagueApiId)
                continue
            }

            val dueWindows = TournamentDueChecker.findDueWindows(windows, existingTournaments, today)
            if (dueWindows.isEmpty()) continue

            try {
                val fetched = runBlocking { apiClient.fetchTournaments(league.leagueApiId) }
                for (apiResponse in fetched) {
                    if (tournamentRepository.findByTournamentApiId(apiResponse.apiId) != null) continue
                    val savedTournament = tournamentRepository.save(apiResponse.toEntity(league))

                    val matchingWindow = TournamentDueChecker.findMatchingWindow(windows, savedTournament.startDate)
                    if (matchingWindow != null) {
                        // API returns tournaments newest-first, so older tournaments are
                        // processed after newer ones. Only advance the anchor forward — never
                        // regress it to an older tournament's start date.
                        if (matchingWindow.startDate.isBefore(savedTournament.startDate)) {
                            matchingWindow.startDate = savedTournament.startDate
                            windowRepository.save(matchingWindow)
                        }
                    } else {
                        log.warn(
                            "New tournament {} for league {} did not match any recurrence window — start_date not advanced",
                            savedTournament.tournamentApiId,
                            league.leagueApiId,
                        )
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to fetch tournaments for league {}", league.leagueApiId, e)
            }
        }
    }
}
