package io.olkkani.lolviewback.adapter.inbound.scheduler

import io.olkkani.lolviewback.adapter.outbound.client.sync.LolEsportsApiClient
import io.olkkani.lolviewback.adapter.outbound.client.sync.mapper.toEntity
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRecurrenceWindowRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.TournamentExternalId
import io.olkkani.lolviewback.application.outbound.ProviderExternalId
import io.olkkani.lolviewback.application.service.ExternalIdLookupService
import io.olkkani.lolviewback.application.service.MissingExternalIdException
import io.olkkani.lolviewback.application.sync.TournamentDueChecker
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
    private val tournamentExternalIdRepository: TournamentExternalIdRepository,
    private val apiClient: LolEsportsApiClient,
    private val externalIdLookupService: ExternalIdLookupService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *")
    fun syncDueTournaments() {
        log.info("Starting tournament sync")
        val today = LocalDate.now()
        val activeLeagues = leagueRepository.findByIsActiveTrue()
        val lolesportsIdsByLeagueId = externalIdLookupService.resolveAllLeagues(activeLeagues, Provider.LOLESPORTS)

        for (league in activeLeagues) {
            val lolesportsId = lolesportsIdsByLeagueId[league.id]
            if (lolesportsId == null) {
                log.warn("League {} has no LOLESPORTS mapping — skipping sync for this league", league.id)
                continue
            }

            val windows = windowRepository.findByLeague(league)
            val existingTournaments = tournamentRepository.findAll().filter { it.league.id == league.id }
            val existingLolesportsIds =
                externalIdLookupService.resolveAllTournaments(existingTournaments, Provider.LOLESPORTS).values.toSet()

            if (TournamentDueChecker.shouldDeactivate(windows, existingTournaments, today)) {
                league.isActive = false
                leagueRepository.save(league)
                log.info("Deactivated league {} — no new tournament found within grace period", league.id)
                continue
            }

            val dueWindows = TournamentDueChecker.findDueWindows(windows, existingTournaments, today)
            if (dueWindows.isEmpty()) continue

            try {
                val fetched =
                    runBlocking { apiClient.fetchTournaments(ProviderExternalId(Provider.LOLESPORTS, lolesportsId)) }
                for (apiResponse in fetched) {
                    if (apiResponse.apiId in existingLolesportsIds) continue
                    val savedTournament = tournamentRepository.save(apiResponse.toEntity(league))
                    tournamentExternalIdRepository.save(
                        TournamentExternalId(
                            entityId = savedTournament.id,
                            provider = Provider.LOLESPORTS,
                            externalId = apiResponse.apiId,
                        ),
                    )

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
                            savedTournament.id,
                            league.id,
                        )
                    }
                }
            } catch (e: MissingExternalIdException) {
                log.warn("Skipping league {} — {}", league.id, e.message)
            } catch (e: Exception) {
                log.error("Failed to fetch tournaments for league {}", league.id, e)
            }
        }
        log.info("Finished tournament sync")
    }
}
