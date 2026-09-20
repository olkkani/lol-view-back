package io.olkkani.lolviewback.adapter.inbound.scheduler

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.client.sync.LolEsportsApiClient
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.TournamentApiResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRecurrenceWindowRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueRecurrenceWindow
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.TournamentExternalId
import io.olkkani.lolviewback.application.outbound.ProviderExternalId
import io.olkkani.lolviewback.application.service.ExternalIdLookupService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TournamentSyncSchedulerTest {

    private val leagueRepository = mockk<LeagueRepository>()
    private val windowRepository = mockk<LeagueRecurrenceWindowRepository>()
    private val tournamentRepository = mockk<TournamentRepository>()
    private val tournamentExternalIdRepository = mockk<TournamentExternalIdRepository>(relaxed = true)
    private val apiClient = mockk<LolEsportsApiClient>()
    private val externalIdLookupService = mockk<ExternalIdLookupService>()

    private val scheduler = TournamentSyncScheduler(
        leagueRepository,
        windowRepository,
        tournamentRepository,
        tournamentExternalIdRepository,
        apiClient,
        externalIdLookupService,
    )

    private fun league(id: Long) = League(id = id, leagueName = "LCK", logoUrl = "https://example.com/lck.png", isActive = true)

    private fun dueWindow(league: League) = LeagueRecurrenceWindow(
        label = "LCK Split 3",
        sequenceOrder = 1,
        league = league,
        intervalYear = 1,
        startDate = LocalDate.now().minusYears(1),
    )

    @Test
    fun `resolves each league's lolesports id via a single batch call before the sync loop`() {
        val leagues = listOf(league(1004L), league(1005L))
        every { leagueRepository.findByIsActiveTrue() } returns leagues
        every { windowRepository.findByLeague(any()) } returns emptyList()
        every { tournamentRepository.findAll() } returns emptyList()
        every {
            externalIdLookupService.resolveAllLeagues(leagues, Provider.LOLESPORTS)
        } returns mapOf(1004L to "lolesports-1004", 1005L to "lolesports-1005")
        every { externalIdLookupService.resolveAllTournaments(emptyList(), Provider.LOLESPORTS) } returns emptyMap()

        scheduler.syncDueTournaments()

        verify(exactly = 1) { externalIdLookupService.resolveAllLeagues(leagues, Provider.LOLESPORTS) }
    }

    @Test
    fun `skips a league with no lolesports mapping before ever querying its recurrence windows`() {
        val mapped = league(1004L)
        val unmapped = league(1005L)
        every { leagueRepository.findByIsActiveTrue() } returns listOf(mapped, unmapped)
        every { windowRepository.findByLeague(mapped) } returns emptyList()
        every { tournamentRepository.findAll() } returns emptyList()
        every {
            externalIdLookupService.resolveAllLeagues(listOf(mapped, unmapped), Provider.LOLESPORTS)
        } returns mapOf(1004L to "lolesports-1004") // 1005L intentionally absent
        every { externalIdLookupService.resolveAllTournaments(emptyList(), Provider.LOLESPORTS) } returns emptyMap()

        // Must not throw despite league 1005 having no mapping.
        scheduler.syncDueTournaments()

        // The unmapped league's null-mapping `continue` must fire before any
        // further per-league work — proven by asserting windowRepository is
        // never consulted for it (only for the mapped league).
        verify(exactly = 0) { windowRepository.findByLeague(unmapped) }
        verify(exactly = 1) { windowRepository.findByLeague(mapped) }
    }

    @Test
    fun `dedups a fetched tournament already present by its resolved lolesports external id`() {
        // Two windows, 6 months apart in calendar position, so findMatchingWindow
        // unambiguously assigns the existing tournament to satisfiedWindow only —
        // dueWindow (this test's real due window) has no matching tournament and
        // is genuinely due, so the sync loop actually reaches fetchTournaments
        // for a real reason, not by accident.
        val league = league(1004L)
        // satisfiedWindow.startDate is exactly 1 year before existingTournament's
        // startDate (same calendar month/day) — findMatchingWindow's circular
        // distance to it is 0, so the existing tournament matches only this
        // window. expectedStart = startDate + 1yr = now - 2mo (in the past —
        // this window's cycle is satisfied by the existing tournament).
        val satisfiedWindow = LeagueRecurrenceWindow(
            label = "LCK Split 2", sequenceOrder = 1, league = league,
            intervalYear = 1, startDate = LocalDate.now().minusMonths(2).minusYears(1),
        )
        // dueWindow's calendar position is 4 months away from
        // existingTournament's (16 total months back vs. 2 months back —
        // circular distance min(4, 8) = 4, vs. satisfiedWindow's distance 0),
        // so the existing tournament never matches it. expectedStart =
        // startDate + 1yr = now - 6mo (also in the past), and with no
        // matching tournament, this window is genuinely, currently due.
        val dueWindow = LeagueRecurrenceWindow(
            label = "LCK Split 3", sequenceOrder = 2, league = league,
            intervalYear = 1, startDate = LocalDate.now().minusMonths(18),
        )
        val existingTournament = Tournament(
            id = 2001L, tournamentName = "LCK Split 2 2026",
            startDate = LocalDate.now().minusMonths(2), endDate = LocalDate.now().minusDays(1), league = league,
        )
        every { leagueRepository.findByIsActiveTrue() } returns listOf(league)
        every { windowRepository.findByLeague(league) } returns listOf(satisfiedWindow, dueWindow)
        every { tournamentRepository.findAll() } returns listOf(existingTournament)
        every {
            externalIdLookupService.resolveAllLeagues(listOf(league), Provider.LOLESPORTS)
        } returns mapOf(1004L to "lolesports-1004")
        every {
            externalIdLookupService.resolveAllTournaments(listOf(existingTournament), Provider.LOLESPORTS)
        } returns mapOf(2001L to "already-synced-id")
        // The fetch response returns ONLY the already-known tournament (by its
        // resolved external id) — proving the dedup check, not the due-window
        // gate, is what prevents the save.
        coEvery {
            apiClient.fetchTournaments(ProviderExternalId(Provider.LOLESPORTS, "lolesports-1004"))
        } returns listOf(
            TournamentApiResponse(apiId = "already-synced-id", name = "LCK Split 2 2026", startDate = LocalDate.now().minusMonths(2), endDate = LocalDate.now().minusDays(1)),
        )

        scheduler.syncDueTournaments()

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
        every { windowRepository.findByLeague(league) } returns listOf(dueWindow(league))
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

        scheduler.syncDueTournaments()

        verify(exactly = 1) {
            tournamentExternalIdRepository.save(
                match<TournamentExternalId> { it.entityId == 3001L && it.provider == Provider.LOLESPORTS && it.externalId == "new-tournament-id" },
            )
        }
    }
}
