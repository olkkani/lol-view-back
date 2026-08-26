package io.olkkani.lolviewback.domain.sync

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueRecurrenceWindow
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.application.sync.TournamentDueChecker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TournamentDueCheckerTest {

    private fun league() = League(
        leagueName = "MSI",
        logoUrl = "https://example.com/msi.png",
        isActive = true,
        leagueApiId = "msi-api-id",
    )

    @Test
    fun `window is not due when today is before expected start year-month`() {
        val league = league()
        val window = LeagueRecurrenceWindow(
            label = "MSI",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 5, 1),
            league = league,
        )
        // expectedStart = 2024-05-01 + 1y = 2025-05-01. today가 그 전이면 due 아님.
        val today = LocalDate.of(2025, 3, 1)

        val dueWindows = TournamentDueChecker.findDueWindows(
            windows = listOf(window),
            existingTournaments = emptyList(),
            today = today,
        )

        assertTrue(dueWindows.isEmpty())
    }

    @Test
    fun `window is due when today reaches expected start year-month and no tournament exists yet`() {
        val league = league()
        val window = LeagueRecurrenceWindow(
            label = "MSI",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 5, 1),
            league = league,
        )
        // expectedStart = 2025-05-01. today가 그 연월 이상.
        val today = LocalDate.of(2025, 5, 1)

        val dueWindows = TournamentDueChecker.findDueWindows(
            windows = listOf(window),
            existingTournaments = emptyList(),
            today = today,
        )

        assertEquals(1, dueWindows.size)
        assertEquals(window, dueWindows[0])
    }

    @Test
    fun `window is not due when a tournament already exists at or after window start date`() {
        val league = league()
        val window = LeagueRecurrenceWindow(
            label = "MSI",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 5, 1),
            league = league,
        )
        val existingTournament = Tournament(
            tournamentName = "MSI 2025",
            startDate = LocalDate.of(2025, 5, 1),
            endDate = LocalDate.of(2025, 5, 20),
            tournamentApiId = "msi-2025",
            league = league,
        )
        val today = LocalDate.of(2025, 6, 1)

        val dueWindows = TournamentDueChecker.findDueWindows(
            windows = listOf(window),
            existingTournaments = listOf(existingTournament),
            today = today,
        )

        assertTrue(dueWindows.isEmpty())
    }

    @Test
    fun `each window in a multi-split league is evaluated independently`() {
        val league = league()
        val springWindow = LeagueRecurrenceWindow(
            label = "Spring",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 1, 1),
            league = league,
        )
        val summerWindow = LeagueRecurrenceWindow(
            label = "Summer",
            sequenceOrder = 2,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 6, 1),
            league = league,
        )
        // Spring 2025는 이미 잡힘 (startDate가 springWindow.startDate=2024-01-01 이후)
        val springTournament = Tournament(
            tournamentName = "LCK 2025 Spring",
            startDate = LocalDate.of(2025, 1, 10),
            endDate = LocalDate.of(2025, 3, 30),
            tournamentApiId = "lck-2025-spring",
            league = league,
        )
        val today = LocalDate.of(2025, 6, 15) // Summer window의 expectedStart(2025-06-01) 이후

        val dueWindows = TournamentDueChecker.findDueWindows(
            windows = listOf(springWindow, summerWindow),
            existingTournaments = listOf(springTournament),
            today = today,
        )

        assertEquals(1, dueWindows.size)
        assertEquals(summerWindow, dueWindows[0])
    }

    @Test
    fun `shouldDeactivate is true when every window exceeds its two-year grace period without a new tournament`() {
        val league = league()
        val window = LeagueRecurrenceWindow(
            label = "MSI",
            sequenceOrder = 1,
            intervalYear = 4,
            startDate = LocalDate.of(2022, 1, 1),
            league = league,
        )
        // expectedStart = 2026-01-01, +2y grace = 2028-01-01
        val today = LocalDate.of(2028, 1, 1)

        val result = TournamentDueChecker.shouldDeactivate(
            windows = listOf(window),
            existingTournaments = emptyList(),
            today = today,
        )

        assertTrue(result)
    }

    @Test
    fun `shouldDeactivate is false when at least one window still has a live tournament cadence`() {
        val league = league()
        val springWindow = LeagueRecurrenceWindow(
            label = "Spring",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 1, 1),
            league = league,
        )
        val summerWindow = LeagueRecurrenceWindow(
            label = "Summer",
            sequenceOrder = 2,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 6, 1),
            league = league,
        )
        // Spring은 정상적으로 계속 열림 (마지막 확인일이 최근으로 계속 갱신됨)
        val recentSpringTournament = Tournament(
            tournamentName = "LCK 2028 Spring",
            startDate = LocalDate.of(2028, 1, 10),
            endDate = LocalDate.of(2028, 3, 30),
            tournamentApiId = "lck-2028-spring",
            league = league,
        )
        // Summer는 2년 넘게 안 열림
        val today = LocalDate.of(2028, 6, 15)

        val result = TournamentDueChecker.shouldDeactivate(
            windows = listOf(springWindow, summerWindow),
            existingTournaments = listOf(recentSpringTournament),
            today = today,
        )

        assertFalse(result)
    }

    @Test
    fun `findMatchingWindow picks the closest preceding window for a given tournament start date`() {
        val league = league()
        val springWindow = LeagueRecurrenceWindow(
            label = "Spring",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 1, 1),
            league = league,
        )
        val summerWindow = LeagueRecurrenceWindow(
            label = "Summer",
            sequenceOrder = 2,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 6, 1),
            league = league,
        )

        val matchedForSpringTournament = TournamentDueChecker.findMatchingWindow(
            windows = listOf(springWindow, summerWindow),
            tournamentStartDate = LocalDate.of(2025, 1, 10),
        )
        val matchedForSummerTournament = TournamentDueChecker.findMatchingWindow(
            windows = listOf(springWindow, summerWindow),
            tournamentStartDate = LocalDate.of(2025, 6, 5),
        )

        assertEquals(springWindow, matchedForSpringTournament)
        assertEquals(summerWindow, matchedForSummerTournament)
    }

    @Test
    fun `findMatchingWindow matches correctly when tournament start date falls a few days before the window's day-of-month`() {
        val league = league()
        // springWindow's recorded day (15th) is AFTER the tournament's day (10th) in the same
        // month. A naive "nearest anniversary on or before" search would snap a full year back
        // to 2024-01-15 (11 months away) instead of recognizing 2025-01-10 is only 5 days after
        // 2025-01-15's neighboring occurrence -- i.e. essentially the same season.
        val springWindow = LeagueRecurrenceWindow(
            label = "Spring",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 1, 15),
            league = league,
        )
        val summerWindow = LeagueRecurrenceWindow(
            label = "Summer",
            sequenceOrder = 2,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 6, 1),
            league = league,
        )

        val matched = TournamentDueChecker.findMatchingWindow(
            windows = listOf(springWindow, summerWindow),
            tournamentStartDate = LocalDate.of(2025, 1, 10),
        )

        assertEquals(springWindow, matched)
    }

    @Test
    fun `findMatchingWindow breaks ties deterministically using sequenceOrder`() {
        val league = league()
        // Two windows placed symmetrically around the tournament's date, so both are equidistant
        // in circular month-distance. The lower sequenceOrder should win regardless of list order.
        val firstWindow = LeagueRecurrenceWindow(
            label = "A",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 1, 1),
            league = league,
        )
        val secondWindow = LeagueRecurrenceWindow(
            label = "B",
            sequenceOrder = 2,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 1, 31),
            league = league,
        )
        val tournamentStartDate = LocalDate.of(2025, 1, 16)

        val matchedInOrder = TournamentDueChecker.findMatchingWindow(
            windows = listOf(firstWindow, secondWindow),
            tournamentStartDate = tournamentStartDate,
        )
        val matchedReverseOrder = TournamentDueChecker.findMatchingWindow(
            windows = listOf(secondWindow, firstWindow),
            tournamentStartDate = tournamentStartDate,
        )

        assertEquals(firstWindow, matchedInOrder)
        assertEquals(firstWindow, matchedReverseOrder)
    }

    @Test
    fun `findDueWindows reports due when the only season-matching tournament predates the window's current anchor`() {
        val league = league()
        // The window's anchor (startDate) has already been advanced to a recent sync point
        // (2025), reflecting the last time this window's cadence was confirmed. Older
        // same-season tournaments (2020, 2013) also exist in history, seeded out of
        // chronological order (newest-first, mirroring the real full-history API response).
        // These older tournaments DO season-match this window via findMatchingWindow (same
        // month/day), but they are NOT the tournament that satisfies the window's *current*
        // cycle -- only a tournament at-or-after the anchor counts as "already handled".
        //
        // Without the recency bound, hasNewTournamentForWindow would find the 2013 (or 2020)
        // tournament matches this window's season and incorrectly report it as satisfied
        // forever, even after the anchor (2025) itself has gone stale and a new cycle is due.
        val window = LeagueRecurrenceWindow(
            label = "MSI",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2025, 5, 1),
            league = league,
        )
        // Deliberately does NOT include a tournament at/after 2025-05-01 -- only stale history
        // remains, seeded out of chronological order.
        val existingTournaments = listOf(
            Tournament(
                tournamentName = "MSI 2020",
                startDate = LocalDate.of(2020, 5, 1),
                endDate = LocalDate.of(2020, 5, 20),
                tournamentApiId = "msi-2020",
                league = league,
            ),
            Tournament(
                tournamentName = "MSI 2013",
                startDate = LocalDate.of(2013, 5, 1),
                endDate = LocalDate.of(2013, 5, 20),
                tournamentApiId = "msi-2013",
                league = league,
            ),
        )
        // expectedStart = 2025-05-01 + 1y = 2026-05-01, long past.
        val today = LocalDate.of(2027, 1, 1)

        val dueWindows = TournamentDueChecker.findDueWindows(
            windows = listOf(window),
            existingTournaments = existingTournaments,
            today = today,
        )

        assertEquals(1, dueWindows.size)
        assertEquals(window, dueWindows[0])
    }
}
