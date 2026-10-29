package io.olkkani.lolviewback.adapter.inbound.scheduler

import io.olkkani.lolviewback.application.service.PollMatchDataService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MatchStateSyncScheduler(
    private val pollMatchDataService: PollMatchDataService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60 * 1000)
    fun syncMatchStates() {
        try {
            runBlocking {
                pollMatchDataService.syncMatchStates()
            }
        } catch (e: Exception) {
            log.error("Failed to sync match states", e)
        }
    }
}
