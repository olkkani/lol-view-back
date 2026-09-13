package io.olkkani.lolviewback.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.client.bracket.PandaScoreClient
import io.olkkani.lolviewback.adapter.outbound.client.bracket.dto.PandaScoreMatch
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentProviderMappingRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.BracketMatchDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.TournamentProviderMapping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import java.time.Instant

class BracketSyncServiceTest {
    @Test
    fun `one tournament's fetch failure does not prevent other tournaments from syncing`() {
        val mappingRepository = mockk<TournamentProviderMappingRepository>()
        val client = mockk<PandaScoreClient>()
        val dao = mockk<BracketMatchDao>(relaxed = true)

        val failingMapping =
            TournamentProviderMapping(
                lolesportsTournamentId = "lol-tournament-1",
                pandascoreTournamentId = "pandascore-1",
            )
        val healthyMapping =
            TournamentProviderMapping(
                lolesportsTournamentId = "lol-tournament-2",
                pandascoreTournamentId = "pandascore-2",
            )

        every { mappingRepository.findAll() } returns listOf(failingMapping, healthyMapping)
        coEvery { client.fetchBrackets("pandascore-1") } throws RuntimeException("PandaScore 500")
        coEvery { client.fetchBrackets("pandascore-2") } returns emptyList<PandaScoreMatch>()

        val service = BracketSyncService(mappingRepository, client, dao)

        runBlocking { service.syncAllMappedTournaments() }

        coVerify(exactly = 1) { dao.upsertMatches("lol-tournament-2", emptyList()) }
    }

    @Test
    fun `does not log a repeated failure for the same tournament within the suppression window`() {
        val mappingRepository = mockk<TournamentProviderMappingRepository>()
        val client = mockk<PandaScoreClient>()
        val dao = mockk<BracketMatchDao>(relaxed = true)
        val log = mockk<Logger>(relaxed = true)

        val mapping =
            TournamentProviderMapping(
                lolesportsTournamentId = "lol-tournament-1",
                pandascoreTournamentId = "pandascore-1",
            )

        every { mappingRepository.findAll() } returns listOf(mapping)
        coEvery { client.fetchBrackets("pandascore-1") } throws RuntimeException("PandaScore 500")

        // Fixed clock: both ticks land at the exact same instant, so this
        // test proves suppression independent of how fast the test executes
        // (a real-clock version would be flaky if it ever ran slower than
        // the 1-hour window, however unlikely that is).
        val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
        val service = BracketSyncService(mappingRepository, client, dao, nowProvider = { fixedNow }, log = log)

        // Two consecutive sync ticks, same failure, well within the
        // suppression window (same instant). The sync attempt itself still
        // happens both times (proven separately by the fetchBrackets
        // verification below), but the second tick's failure must NOT
        // reach the logger — this is the actual behavior under test, not a
        // proxy for it.
        runBlocking {
            service.syncAllMappedTournaments()
            service.syncAllMappedTournaments()
        }

        coVerify(exactly = 2) { client.fetchBrackets("pandascore-1") }
        verify(exactly = 1) { log.error(any(), any<Throwable>()) }
    }

    @Test
    fun `does log again once the suppression window has elapsed`() {
        val mappingRepository = mockk<TournamentProviderMappingRepository>()
        val client = mockk<PandaScoreClient>()
        val dao = mockk<BracketMatchDao>(relaxed = true)
        val log = mockk<Logger>(relaxed = true)

        val mapping =
            TournamentProviderMapping(
                lolesportsTournamentId = "lol-tournament-1",
                pandascoreTournamentId = "pandascore-1",
            )

        every { mappingRepository.findAll() } returns listOf(mapping)
        coEvery { client.fetchBrackets("pandascore-1") } throws RuntimeException("PandaScore 500")

        var now = Instant.parse("2026-01-01T00:00:00Z")
        val service = BracketSyncService(mappingRepository, client, dao, nowProvider = { now }, log = log)

        runBlocking {
            service.syncAllMappedTournaments()
            // Advance well past the 1-hour suppression window.
            now = now.plusSeconds(3_600 * 2)
            service.syncAllMappedTournaments()
        }

        coVerify(exactly = 2) { client.fetchBrackets("pandascore-1") }
        verify(exactly = 2) { log.error(any(), any<Throwable>()) }
    }

    @Test
    fun `propagates CancellationException instead of treating it as an ordinary sync failure`() {
        val mappingRepository = mockk<TournamentProviderMappingRepository>()
        val client = mockk<PandaScoreClient>()
        val dao = mockk<BracketMatchDao>(relaxed = true)

        val mapping =
            TournamentProviderMapping(
                lolesportsTournamentId = "lol-tournament-1",
                pandascoreTournamentId = "pandascore-1",
            )

        every { mappingRepository.findAll() } returns listOf(mapping)
        coEvery { client.fetchBrackets("pandascore-1") } throws CancellationException("coroutine cancelled")

        val service = BracketSyncService(mappingRepository, client, dao)

        assertThrows(CancellationException::class.java) {
            runBlocking { service.syncAllMappedTournaments() }
        }
    }
}
