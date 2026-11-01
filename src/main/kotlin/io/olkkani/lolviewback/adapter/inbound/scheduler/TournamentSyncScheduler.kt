package io.olkkani.lolviewback.adapter.inbound.scheduler

import io.olkkani.lolviewback.application.service.TournamentSyncService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TournamentSyncScheduler(
    private val tournamentSyncService: TournamentSyncService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 1 * *")
    fun syncNewTournaments() {
        log.info("Starting tournament sync")
        try {
            runBlocking {
                tournamentSyncService.syncNewTournaments()
            }
            log.info("Finished tournament sync")
        } catch (e: Exception) {
            log.error("Failed to sync tournaments", e)
        }
    }
}
