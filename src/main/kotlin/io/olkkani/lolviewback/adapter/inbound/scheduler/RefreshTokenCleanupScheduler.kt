package io.olkkani.lolviewback.adapter.inbound.scheduler

import io.olkkani.lolviewback.adapter.outbound.persistence.RefreshTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RefreshTokenCleanupScheduler(
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *")
    fun cleanup() {
        try {
            val deletedCount = refreshTokenRepository.deleteAllExpiredBefore(LocalDateTime.now())
            log.info("Deleted $deletedCount expired refresh tokens")
        } catch (e: Exception) {
            log.error("Failed to clean up expired refresh tokens", e)
        }
    }
}
