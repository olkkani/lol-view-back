package io.olkkani.lolviewback.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.persistence.ClubExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Club
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubExternalId
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueExternalId
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.TournamentExternalId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ExternalIdLookupServiceTest {

    private val leagueExternalIdRepository = mockk<LeagueExternalIdRepository>()
    private val tournamentExternalIdRepository = mockk<TournamentExternalIdRepository>()
    private val clubExternalIdRepository = mockk<ClubExternalIdRepository>()

    private val service = ExternalIdLookupService(
        leagueExternalIdRepository,
        tournamentExternalIdRepository,
        clubExternalIdRepository,
    )

    private fun league(id: Long = 1004L) = League(
        id = id,
        leagueName = "LCK",
        logoUrl = "https://example.com/lck.png",
        isActive = true,
        leagueApiId = "legacy-api-id",
    )

    private fun tournament(id: Long = 2001L, league: League = league()) = Tournament(
        id = id,
        tournamentName = "LCK 2026 Spring",
        startDate = LocalDate.of(2026, 1, 1),
        endDate = LocalDate.of(2026, 4, 1),
        tournamentApiId = "legacy-tournament-api-id",
        league = league,
    )

    private fun club(id: Long = 3001L) = Club(
        id = id,
        isActive = true,
    )

    // --- resolveLeague ---

    @Test
    fun `resolveLeague returns the external id when a mapping exists`() {
        every {
            leagueExternalIdRepository.findByEntityIdAndProvider(1004L, Provider.LOLESPORTS)
        } returns LeagueExternalId(entityId = 1004L, provider = Provider.LOLESPORTS, externalId = "98767991310872058")

        val result = service.resolveLeague(league(1004L), Provider.LOLESPORTS)

        assertEquals("98767991310872058", result)
    }

    @Test
    fun `resolveLeague throws MissingExternalIdException when no mapping exists`() {
        every {
            leagueExternalIdRepository.findByEntityIdAndProvider(9999L, Provider.LOLESPORTS)
        } returns null

        assertThrows(MissingExternalIdException::class.java) {
            service.resolveLeague(league(9999L), Provider.LOLESPORTS)
        }
    }

    // --- resolveTournament ---

    @Test
    fun `resolveTournament returns the external id when a mapping exists`() {
        every {
            tournamentExternalIdRepository.findByEntityIdAndProvider(2001L, Provider.PANDASCORE)
        } returns TournamentExternalId(entityId = 2001L, provider = Provider.PANDASCORE, externalId = "pandascore-tourney-1")

        val result = service.resolveTournament(tournament(2001L), Provider.PANDASCORE)

        assertEquals("pandascore-tourney-1", result)
    }

    @Test
    fun `resolveTournament throws MissingExternalIdException when no mapping exists`() {
        every {
            tournamentExternalIdRepository.findByEntityIdAndProvider(9999L, Provider.PANDASCORE)
        } returns null

        assertThrows(MissingExternalIdException::class.java) {
            service.resolveTournament(tournament(9999L), Provider.PANDASCORE)
        }
    }

    // --- resolveClub ---

    @Test
    fun `resolveClub returns the external id when a mapping exists`() {
        every {
            clubExternalIdRepository.findByEntityIdAndProvider(3001L, Provider.LOLESPORTS)
        } returns ClubExternalId(entityId = 3001L, provider = Provider.LOLESPORTS, externalId = "club-external-1")

        val result = service.resolveClub(club(3001L), Provider.LOLESPORTS)

        assertEquals("club-external-1", result)
    }

    @Test
    fun `resolveClub throws MissingExternalIdException when no mapping exists`() {
        every {
            clubExternalIdRepository.findByEntityIdAndProvider(9999L, Provider.LOLESPORTS)
        } returns null

        assertThrows(MissingExternalIdException::class.java) {
            service.resolveClub(club(9999L), Provider.LOLESPORTS)
        }
    }

    // --- resolveAllLeagues ---

    @Test
    fun `resolveAllLeagues issues a single batch query and returns entity id to external id`() {
        val leagues = listOf(league(1001L), league(1002L))
        every {
            leagueExternalIdRepository.findByEntityIdInAndProvider(listOf(1001L, 1002L), Provider.LOLESPORTS)
        } returns listOf(
            LeagueExternalId(entityId = 1001L, provider = Provider.LOLESPORTS, externalId = "a"),
            LeagueExternalId(entityId = 1002L, provider = Provider.LOLESPORTS, externalId = "b"),
        )

        val result = service.resolveAllLeagues(leagues, Provider.LOLESPORTS)

        assertEquals(mapOf(1001L to "a", 1002L to "b"), result)
        verify(exactly = 1) {
            leagueExternalIdRepository.findByEntityIdInAndProvider(listOf(1001L, 1002L), Provider.LOLESPORTS)
        }
    }

    @Test
    fun `resolveAllLeagues omits entities with no mapping instead of throwing`() {
        val leagues = listOf(league(1001L), league(1002L))
        every {
            leagueExternalIdRepository.findByEntityIdInAndProvider(listOf(1001L, 1002L), Provider.LOLESPORTS)
        } returns listOf(
            LeagueExternalId(entityId = 1001L, provider = Provider.LOLESPORTS, externalId = "a"),
        )

        val result = service.resolveAllLeagues(leagues, Provider.LOLESPORTS)

        assertEquals(mapOf(1001L to "a"), result)
    }

    @Test
    fun `resolveAllLeagues returns an empty map for an empty entity list without querying`() {
        val result = service.resolveAllLeagues(emptyList(), Provider.LOLESPORTS)

        assertEquals(emptyMap<Long, String>(), result)
    }

    // --- resolveAllTournaments ---

    @Test
    fun `resolveAllTournaments issues a single batch query and returns entity id to external id`() {
        val tournaments = listOf(tournament(2001L), tournament(2002L))
        every {
            tournamentExternalIdRepository.findByEntityIdInAndProvider(listOf(2001L, 2002L), Provider.PANDASCORE)
        } returns listOf(
            TournamentExternalId(entityId = 2001L, provider = Provider.PANDASCORE, externalId = "x"),
            TournamentExternalId(entityId = 2002L, provider = Provider.PANDASCORE, externalId = "y"),
        )

        val result = service.resolveAllTournaments(tournaments, Provider.PANDASCORE)

        assertEquals(mapOf(2001L to "x", 2002L to "y"), result)
        verify(exactly = 1) {
            tournamentExternalIdRepository.findByEntityIdInAndProvider(listOf(2001L, 2002L), Provider.PANDASCORE)
        }
    }

    @Test
    fun `resolveAllTournaments omits entities with no mapping instead of throwing`() {
        val tournaments = listOf(tournament(2001L), tournament(2002L))
        every {
            tournamentExternalIdRepository.findByEntityIdInAndProvider(listOf(2001L, 2002L), Provider.PANDASCORE)
        } returns listOf(
            TournamentExternalId(entityId = 2001L, provider = Provider.PANDASCORE, externalId = "x"),
        )

        val result = service.resolveAllTournaments(tournaments, Provider.PANDASCORE)

        assertEquals(mapOf(2001L to "x"), result)
    }

    @Test
    fun `resolveAllTournaments returns an empty map for an empty entity list without querying`() {
        val result = service.resolveAllTournaments(emptyList(), Provider.PANDASCORE)

        assertEquals(emptyMap<Long, String>(), result)
    }
}
