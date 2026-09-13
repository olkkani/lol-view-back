package io.olkkani.lolviewback.adapter.inbound.scheduler

import io.olkkani.lolviewback.application.service.BracketSyncService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BracketSyncScheduler(
    private val bracketSyncService: BracketSyncService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 5 * 60 * 1000)
    fun syncBrackets() {
        log.info("Starting bracket sync")
        try {
            runBlocking {
                bracketSyncService.syncAllMappedTournaments()
            }
            log.info("Finished bracket sync")
        } catch (e: Exception) {
            log.error("Failed to sync brackets", e)
        }
    }
}
