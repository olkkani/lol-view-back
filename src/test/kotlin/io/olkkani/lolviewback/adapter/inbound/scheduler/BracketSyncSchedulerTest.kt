package io.olkkani.lolviewback.adapter.inbound.scheduler

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.olkkani.lolviewback.application.service.BracketSyncService
import org.junit.jupiter.api.Test

class BracketSyncSchedulerTest {

    @Test
    fun `invokes the sync service and does not propagate an exception`() {
        val service = mockk<BracketSyncService>()
        coEvery { service.syncAllMappedTournaments() } throws RuntimeException("boom")

        val scheduler = BracketSyncScheduler(service)

        // Must not throw — matching MatchSetSyncScheduler's try/catch-and-log
        // convention, since an uncaught exception here would kill the
        // scheduled task permanently in Spring's default TaskScheduler.
        scheduler.syncBrackets()

        coVerify(exactly = 1) { service.syncAllMappedTournaments() }
    }
}
