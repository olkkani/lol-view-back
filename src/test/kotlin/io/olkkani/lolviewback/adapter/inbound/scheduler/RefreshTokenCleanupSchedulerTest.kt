package io.olkkani.lolviewback.adapter.inbound.scheduler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.persistence.RefreshTokenRepository
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RefreshTokenCleanupSchedulerTest {

    private val repository = mockk<RefreshTokenRepository>()
    private val scheduler = RefreshTokenCleanupScheduler(repository)

    @Test
    fun `cleanup deletes rows expired before now`() {
        every { repository.deleteAllExpiredBefore(any()) } returns 5

        scheduler.cleanup()

        verify { repository.deleteAllExpiredBefore(any<LocalDateTime>()) }
    }

    @Test
    fun `cleanup does not throw when the repository call fails`() {
        every { repository.deleteAllExpiredBefore(any()) } throws RuntimeException("db down")

        scheduler.cleanup()
    }
}
