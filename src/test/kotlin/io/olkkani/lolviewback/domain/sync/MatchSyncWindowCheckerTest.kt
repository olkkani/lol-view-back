package io.olkkani.lolviewback.domain.sync

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.application.sync.MatchSyncWindowChecker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MatchSyncWindowCheckerTest {

    private fun league(apiId: String = "lck-api-id") = League(
        leagueName = "LCK",
        logoUrl = "https://example.com/lck.png",
        isActive = true,
        leagueApiId = apiId,
    )

    private fun tournament(
        league: League,
        startDate: LocalDate,
        endDate: LocalDate,
        apiId: String = "t-1",
    ) = Tournament(
        tournamentName = "LCK 2026 Spring",
        startDate = startDate,
        endDate = endDate,
        tournamentApiId = apiId,
        league = league,
    )

    @Test
    fun `tournament is selected when today falls within its 7-day-before-start to end window`() {
        val league = league()
        val tournament = tournament(
            league = league,
            startDate = LocalDate.of(2026, 3, 10),
            endDate = LocalDate.of(2026, 5, 1),
        )
        val today = LocalDate.of(2026, 3, 5) // 5 days before start, inside the 7-day window

        val result = MatchSyncWindowChecker.selectTournamentsToSync(listOf(tournament), today)

        assertEquals(1, result.size)
        assertEquals(tournament, result[0].tournament)
        assertTrue(result[0].skippedDueToMultipleTriggers.isEmpty())
    }

    @Test
    fun `tournament is not selected when today is before the 7-day window starts`() {
        val league = league()
        val tournament = tournament(
            league = league,
            startDate = LocalDate.of(2026, 3, 10),
            endDate = LocalDate.of(2026, 5, 1),
        )
        val today = LocalDate.of(2026, 3, 2) // 8 days before start, outside the window

        val result = MatchSyncWindowChecker.selectTournamentsToSync(listOf(tournament), today)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `tournament is not selected when today is after the tournament end date`() {
        val league = league()
        val tournament = tournament(
            league = league,
            startDate = LocalDate.of(2026, 3, 10),
            endDate = LocalDate.of(2026, 5, 1),
        )
        val today = LocalDate.of(2026, 5, 2)

        val result = MatchSyncWindowChecker.selectTournamentsToSync(listOf(tournament), today)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `today exactly on window start boundary is selected (inclusive)`() {
        val league = league()
        val tournament = tournament(
            league = league,
            startDate = LocalDate.of(2026, 3, 10),
            endDate = LocalDate.of(2026, 5, 1),
        )
        val today = LocalDate.of(2026, 3, 3) // exactly startDate.minusDays(7)

        val result = MatchSyncWindowChecker.selectTournamentsToSync(listOf(tournament), today)

        assertEquals(1, result.size)
        assertEquals(tournament, result[0].tournament)
    }

    @Test
    fun `today exactly on tournament end date is selected (inclusive)`() {
        val league = league()
        val tournament = tournament(
            league = league,
            startDate = LocalDate.of(2026, 3, 10),
            endDate = LocalDate.of(2026, 5, 1),
        )
        val today = LocalDate.of(2026, 5, 1)

        val result = MatchSyncWindowChecker.selectTournamentsToSync(listOf(tournament), today)

        assertEquals(1, result.size)
        assertEquals(tournament, result[0].tournament)
    }

    @Test
    fun `league with two concurrently-triggering tournaments is reported as skipped and not selected`() {
        val league = league()
        val tournamentA = tournament(
            league = league,
            startDate = LocalDate.of(2026, 3, 10),
            endDate = LocalDate.of(2026, 5, 1),
            apiId = "t-a",
        )
        val tournamentB = tournament(
            league = league,
            startDate = LocalDate.of(2026, 3, 12),
            endDate = LocalDate.of(2026, 5, 5),
            apiId = "t-b",
        )
        val today = LocalDate.of(2026, 3, 10) // inside both windows

        val result = MatchSyncWindowChecker.selectTournamentsToSync(listOf(tournamentA, tournamentB), today)

        assertEquals(1, result.size)
        assertNull(result[0].tournament)
        assertEquals(2, result[0].skippedDueToMultipleTriggers.size)
        assertTrue(result[0].skippedDueToMultipleTriggers.containsAll(listOf(tournamentA, tournamentB)))
    }

    @Test
    fun `tournaments in different leagues are each evaluated independently`() {
        val leagueA = league(apiId = "lck-api-id")
        val leagueB = league(apiId = "lpl-api-id")
        val tournamentA = tournament(
            league = leagueA,
            startDate = LocalDate.of(2026, 3, 10),
            endDate = LocalDate.of(2026, 5, 1),
            apiId = "t-a",
        )
        val tournamentB = tournament(
            league = leagueB,
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 6, 1),
            apiId = "t-b",
        )
        val today = LocalDate.of(2026, 3, 10) // inside A's window, outside B's window

        val result = MatchSyncWindowChecker.selectTournamentsToSync(listOf(tournamentA, tournamentB), today)

        assertEquals(1, result.size)
        assertEquals(tournamentA, result[0].tournament)
    }
}
