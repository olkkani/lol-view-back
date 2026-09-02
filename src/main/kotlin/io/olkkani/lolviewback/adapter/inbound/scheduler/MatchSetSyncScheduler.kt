package io.olkkani.lolviewback.adapter.inbound.scheduler

import io.olkkani.lolviewback.application.service.PollMatchSetService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MatchSetSyncScheduler(
    private val pollMatchSetService: PollMatchSetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 5 * 60 * 1000)
    fun syncMatchSet() {
        try {
            runBlocking {
                pollMatchSetService.syncMatchSets()
            }
        } catch (e: Exception) {
            log.error("Failed to sync match sets", e)
        }
    }
}