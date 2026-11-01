package io.olkkani.lolviewback.adapter.inbound.scheduler

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.olkkani.lolviewback.application.service.TournamentSyncService
import org.junit.jupiter.api.Test

class TournamentSyncSchedulerTest {

    @Test
    fun `invokes the sync service and does not propagate an exception`() {
        val service = mockk<TournamentSyncService>()
        coEvery { service.syncNewTournaments() } throws RuntimeException("boom")

        val scheduler = TournamentSyncScheduler(service)

        // Must not throw — matching BracketSyncScheduler's try/catch-and-log
        // convention, since an uncaught exception here would kill the
        // scheduled task permanently in Spring's default TaskScheduler.
        scheduler.syncNewTournaments()

        coVerify(exactly = 1) { service.syncNewTournaments() }
    }
}
