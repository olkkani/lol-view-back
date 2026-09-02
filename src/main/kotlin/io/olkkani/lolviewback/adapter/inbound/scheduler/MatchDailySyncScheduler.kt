package io.olkkani.lolviewback.adapter.inbound.scheduler

import io.olkkani.lolviewback.application.service.PollMatchDataService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MatchDailySyncScheduler(
    private val pollMatchDataService: PollMatchDataService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 5 0 * * *")
    fun syncUpcomingMatches() {
        try {
            runBlocking {
                pollMatchDataService.syncUpcomingMatches()
            }
        } catch (e: Exception) {
            log.error("Failed to sync upcoming matches", e)
        }
    }
}
