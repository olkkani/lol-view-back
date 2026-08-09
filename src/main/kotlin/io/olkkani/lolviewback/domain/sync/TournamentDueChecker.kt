package io.olkkani.lolviewback.domain.sync

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.LeagueRecurrenceWindow
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth

object TournamentDueChecker {

    fun findDueWindows(
        windows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
        today: LocalDate,
    ): List<LeagueRecurrenceWindow> {
        return windows.filter { window -> isWindowDue(window, windows, existingTournaments, today) }
    }

    fun shouldDeactivate(
        windows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
        today: LocalDate,
    ): Boolean {
        if (windows.isEmpty()) return false
        return windows.all { window -> isWindowOverdue(window, windows, existingTournaments, today) }
    }

    fun findMatchingWindow(
        windows: List<LeagueRecurrenceWindow>,
        tournamentStartDate: LocalDate,
    ): LeagueRecurrenceWindow? {
        // Match each window's most recent recurrence anniversary (same month/day, on or before
        // tournamentStartDate) and pick whichever window's anniversary is closest. A plain
        // "latest window.startDate <= tournamentStartDate" comparison is WRONG here: an older
        // window (e.g. Spring, started 2024-01-01) can still be the correct match for a later
        // tournament (e.g. one starting 2025-01-10) even though a newer window (e.g. Summer,
        // started 2024-06-01) has a startDate that is chronologically closer in absolute terms.
        return windows
            .mapNotNull { window ->
                val anniversary = mostRecentAnniversaryOnOrBefore(window.startDate, tournamentStartDate)
                    ?: return@mapNotNull null
                window to Period.between(anniversary, tournamentStartDate).let { it.years * 12 + it.months }
            }
            .minByOrNull { (_, monthsSinceAnniversary) -> monthsSinceAnniversary }
            ?.first
    }

    private fun mostRecentAnniversaryOnOrBefore(windowStartDate: LocalDate, reference: LocalDate): LocalDate? {
        val candidate = windowStartDate.withYear(reference.year)
        val adjusted = if (candidate.isAfter(reference)) candidate.minusYears(1) else candidate
        return if (adjusted.isBefore(windowStartDate)) null else adjusted
    }

    private fun expectedStart(window: LeagueRecurrenceWindow): LocalDate {
        return window.startDate.plusYears(window.intervalYear.toLong())
    }

    private fun hasNewTournamentForWindow(
        window: LeagueRecurrenceWindow,
        allWindows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
    ): Boolean {
        return existingTournaments.any { tournament -> findMatchingWindow(allWindows, tournament.startDate) == window }
    }

    private fun isWindowDue(
        window: LeagueRecurrenceWindow,
        allWindows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
        today: LocalDate,
    ): Boolean {
        val expected = expectedStart(window)
        val isPastExpectedYearMonth = !YearMonth.from(today).isBefore(YearMonth.from(expected))
        return isPastExpectedYearMonth && !hasNewTournamentForWindow(window, allWindows, existingTournaments)
    }

    private fun isWindowOverdue(
        window: LeagueRecurrenceWindow,
        allWindows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
        today: LocalDate,
    ): Boolean {
        val deactivationThreshold = expectedStart(window).plusYears(2)
        val isPastGracePeriod = !today.isBefore(deactivationThreshold)
        return isPastGracePeriod && !hasNewTournamentForWindow(window, allWindows, existingTournaments)
    }
}
