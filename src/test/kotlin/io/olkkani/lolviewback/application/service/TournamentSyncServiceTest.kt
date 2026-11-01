package io.olkkani.lolviewback.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.client.sync.LolEsportsApiClient
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.TournamentApiResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.TournamentExternalId
import io.olkkani.lolviewback.application.outbound.ProviderExternalId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TournamentSyncServiceTest {

    private val leagueRepository = mockk<LeagueRepository>()
    private val tournamentRepository = mockk<TournamentRepository>()
    private val tournamentExternalIdRepository = mockk<TournamentExternalIdRepository>(relaxed = true)
    private val apiClient = mockk<LolEsportsApiClient>()
    private val externalIdLookupService = mockk<ExternalIdLookupService>()

    private val service = TournamentSyncService(
        leagueRepository,
        tournamentRepository,
        tournamentExternalIdRepository,
        apiClient,
        externalIdLookupService,
    )

    private fun league(id: Long) = League(id = id, leagueName = "LCK", logoUrl = "https://example.com/lck.png", isActive = true)

    @Test
    fun `resolves each league's lolesports id via a single batch call before the sync loop`() {
        val leagues = listOf(league(1004L), league(1005L))
        every { leagueRepository.findByIsActiveTrue() } returns leagues
        every { tournamentRepository.findAll() } returns emptyList()
        every {
            externalIdLookupService.resolveAllLeagues(leagues, Provider.LOLESPORTS)
        } returns mapOf(1004L to "lolesports-1004", 1005L to "lolesports-1005")
        every { externalIdLookupService.resolveAllTournaments(emptyList(), Provider.LOLESPORTS) } returns emptyMap()

        runBlocking { service.syncNewTournaments() }

        verify(exactly = 1) { externalIdLookupService.resolveAllLeagues(leagues, Provider.LOLESPORTS) }
    }

    @Test
    fun `skips a league with no lolesports mapping without fetching its tournaments`() {
        val mapped = league(1004L)
        val unmapped = league(1005L)
        every { leagueRepository.findByIsActiveTrue() } returns listOf(mapped, unmapped)
        every { tournamentRepository.findAll() } returns emptyList()
        every {
            externalIdLookupService.resolveAllLeagues(listOf(mapped, unmapped), Provider.LOLESPORTS)
        } returns mapOf(1004L to "lolesports-1004") // 1005L intentionally absent
        every { externalIdLookupService.resolveAllTournaments(emptyList(), Provider.LOLESPORTS) } returns emptyMap()
        coEvery { apiClient.fetchTournaments(any()) } returns emptyList()

        // Must not throw despite league 1005 having no mapping.
        runBlocking { service.syncNewTournaments() }

        coVerify(exactly = 1) { apiClient.fetchTournaments(any()) }
    }

    @Test
    fun `dedups a fetched tournament already present by its resolved lolesports external id`() {
        val league = league(1004L)
        val existingTournament = Tournament(
            id = 2001L, tournamentName = "LCK Split 2 2026",
            startDate = LocalDate.now().minusMonths(2), endDate = LocalDate.now().minusDays(1), league = league,
        )
        every { leagueRepository.findByIsActiveTrue() } returns listOf(league)
        every { tournamentRepository.findAll() } returns listOf(existingTournament)
        every {
            externalIdLookupService.resolveAllLeagues(listOf(league), Provider.LOLESPORTS)
        } returns mapOf(1004L to "lolesports-1004")
        every {
            externalIdLookupService.resolveAllTournaments(listOf(existingTournament), Provider.LOLESPORTS)
        } returns mapOf(2001L to "already-synced-id")
        // The fetch response returns ONLY the already-known tournament (by its
        // resolved external id) — proving the dedup check is what prevents the save.
        coEvery {
            apiClient.fetchTournaments(ProviderExternalId(Provider.LOLESPORTS, "lolesports-1004"))
        } returns listOf(
            TournamentApiResponse(apiId = "already-synced-id", name = "LCK Split 2 2026", startDate = LocalDate.now().minusMonths(2), endDate = LocalDate.now().minusDays(1)),
        )

        runBlocking { service.syncNewTournaments() }

        verify(exactly = 0) { tournamentRepository.save(any()) }
    }

    @Test
    fun `writes a new tournament_external_ids row for each newly synced tournament`() {
        val league = league(1004L)
        val newTournamentSlot = slot<Tournament>()
        val savedTournament = Tournament(
            id = 3001L, tournamentName = "LCK Split 3 2026",
            startDate = LocalDate.now(), endDate = LocalDate.now().plusDays(60), league = league,
        )
        every { leagueRepository.findByIsActiveTrue() } returns listOf(league)
        every { tournamentRepository.findAll() } returns emptyList()
        every {
            externalIdLookupService.resolveAllLeagues(listOf(league), Provider.LOLESPORTS)
        } returns mapOf(1004L to "lolesports-1004")
        every { externalIdLookupService.resolveAllTournaments(emptyList(), Provider.LOLESPORTS) } returns emptyMap()
        coEvery {
            apiClient.fetchTournaments(ProviderExternalId(Provider.LOLESPORTS, "lolesports-1004"))
        } returns listOf(
            TournamentApiResponse(apiId = "new-tournament-id", name = "LCK Split 3 2026", startDate = LocalDate.now(), endDate = LocalDate.now().plusDays(60)),
        )
        every { tournamentRepository.save(capture(newTournamentSlot)) } returns savedTournament
        every { tournamentExternalIdRepository.save(any()) } returnsArgument 0

        runBlocking { service.syncNewTournaments() }

        verify(exactly = 1) {
            tournamentExternalIdRepository.save(
                match<TournamentExternalId> { it.entityId == 3001L && it.provider == Provider.LOLESPORTS && it.externalId == "new-tournament-id" },
            )
        }
    }
}
