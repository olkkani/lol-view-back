package io.olkkani.lolviewback.domain.sync

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.LeagueRecurrenceWindow
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import java.time.LocalDate
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
        // Match by which window's recurrence "season" (month/day, independent of year) is
        // closest to the tournament's start date, measured symmetrically in either direction
        // around the calendar (circular month distance), not just "nearest anniversary on or
        // before". A one-directional (backwards-only) comparison is WRONG: a tournament that
        // starts a few days EARLIER in the year than a window's recorded day (e.g. window day
        // 15, tournament day 10) would otherwise snap a full year further back instead of a few
        // days forward, producing a huge false distance and picking the wrong window.
        //
        // Ties are broken by sequenceOrder for determinism (lower sequenceOrder wins), since
        // otherwise input-list order would silently decide the outcome.
        return windows
            .map { window -> window to circularMonthDistance(window.startDate, tournamentStartDate) }
            .sortedWith(compareBy({ (_, distance) -> distance }, { (window, _) -> window.sequenceOrder }))
            .firstOrNull()
            ?.first
    }

    /**
     * Distance, in months, between two dates' month/day markers, ignoring year and measured in
     * whichever direction (forward or backward around the 12-month calendar) is shorter. E.g.
     * Jan 1 and Dec 20 are ~1.3 months apart, not ~11 months apart.
     */
    private fun circularMonthDistance(a: LocalDate, b: LocalDate): Double {
        val aPosition = a.monthValue + a.dayOfMonth / 31.0
        val bPosition = b.monthValue + b.dayOfMonth / 31.0
        val diff = kotlin.math.abs(aPosition - bPosition)
        return minOf(diff, 12.0 - diff)
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
