package io.olkkani.lolviewback.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.client.bracket.PandaScoreClient
import io.olkkani.lolviewback.adapter.outbound.client.bracket.dto.PandaScoreMatch
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.BracketMatchDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import java.time.Instant
import java.time.LocalDate

class BracketSyncServiceTest {

    private val tournamentRepository = mockk<TournamentRepository>()
    private val externalIdLookupService = mockk<ExternalIdLookupService>()
    private val client = mockk<PandaScoreClient>()
    private val dao = mockk<BracketMatchDao>(relaxed = true)

    private fun league() = League(leagueName = "LCK", logoUrl = "https://example.com/lck.png", isActive = true)

    private fun tournament(id: Long) = Tournament(
        id = id,
        tournamentName = "LCK Summer 2026",
        startDate = LocalDate.now(),
        endDate = LocalDate.now().plusDays(30),
        league = league(),
    )

    @Test
    fun `only syncs tournaments with both LOLESPORTS and PANDASCORE mappings`() {
        val tournament101 = tournament(101L)
        every {
            tournamentRepository.findTournamentIdsWithBothProviders(Provider.LOLESPORTS, Provider.PANDASCORE)
        } returns listOf(101L)
        every { tournamentRepository.findAllById(listOf(101L)) } returns listOf(tournament101)
        every { externalIdLookupService.resolveTournament(tournament101, Provider.LOLESPORTS) } returns "lol-101"
        every { externalIdLookupService.resolveTournament(tournament101, Provider.PANDASCORE) } returns "panda-101"
        coEvery { client.fetchBrackets("panda-101") } returns emptyList<PandaScoreMatch>()

        val service = BracketSyncService(tournamentRepository, externalIdLookupService, client, dao)
        runBlocking { service.syncAllMappedTournaments() }

        coVerify(exactly = 1) { dao.upsertMatches("lol-101", emptyList()) }
    }

    @Test
    fun `one tournament's fetch failure does not prevent other tournaments from syncing`() {
        val tournament101 = tournament(101L)
        val tournament102 = tournament(102L)
        every {
            tournamentRepository.findTournamentIdsWithBothProviders(Provider.LOLESPORTS, Provider.PANDASCORE)
        } returns listOf(101L, 102L)
        every { tournamentRepository.findAllById(listOf(101L, 102L)) } returns listOf(tournament101, tournament102)
        every { externalIdLookupService.resolveTournament(tournament101, Provider.LOLESPORTS) } returns "lol-101"
        every { externalIdLookupService.resolveTournament(tournament101, Provider.PANDASCORE) } returns "panda-101"
        every { externalIdLookupService.resolveTournament(tournament102, Provider.LOLESPORTS) } returns "lol-102"
        every { externalIdLookupService.resolveTournament(tournament102, Provider.PANDASCORE) } returns "panda-102"
        coEvery { client.fetchBrackets("panda-101") } throws RuntimeException("PandaScore 500")
        coEvery { client.fetchBrackets("panda-102") } returns emptyList<PandaScoreMatch>()

        val service = BracketSyncService(tournamentRepository, externalIdLookupService, client, dao)
        runBlocking { service.syncAllMappedTournaments() }

        coVerify(exactly = 1) { dao.upsertMatches("lol-102", emptyList()) }
    }

    @Test
    fun `does not log a repeated failure for the same tournament within the suppression window`() {
        val tournament101 = tournament(101L)
        every {
            tournamentRepository.findTournamentIdsWithBothProviders(Provider.LOLESPORTS, Provider.PANDASCORE)
        } returns listOf(101L)
        every { tournamentRepository.findAllById(listOf(101L)) } returns listOf(tournament101)
        every { externalIdLookupService.resolveTournament(tournament101, Provider.LOLESPORTS) } returns "lol-101"
        every { externalIdLookupService.resolveTournament(tournament101, Provider.PANDASCORE) } returns "panda-101"
        coEvery { client.fetchBrackets("panda-101") } throws RuntimeException("PandaScore 500")
        val log = mockk<Logger>(relaxed = true)

        val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
        val service = BracketSyncService(
            tournamentRepository, externalIdLookupService, client, dao,
            nowProvider = { fixedNow }, log = log,
        )

        runBlocking {
            service.syncAllMappedTournaments()
            service.syncAllMappedTournaments()
        }

        verify(exactly = 1) { log.error(any<String>(), any<Throwable>()) }
    }
}
