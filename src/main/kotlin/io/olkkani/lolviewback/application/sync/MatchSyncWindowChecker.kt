package io.olkkani.lolviewback.application.sync

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import java.time.LocalDate

data class LeagueSyncSelection(
    val tournament: Tournament?,
    val skippedDueToMultipleTriggers: List<Tournament> = emptyList(),
)

object MatchSyncWindowChecker {

    fun selectTournamentsToSync(
        allTournaments: List<Tournament>,
        today: LocalDate,
    ): List<LeagueSyncSelection> {
        val triggeringTournaments = allTournaments.filter { tournament -> isInSyncWindow(tournament, today) }
        val byLeague = triggeringTournaments.groupBy { it.league.id }

        return byLeague.values.map { tournamentsForLeague ->
            if (tournamentsForLeague.size > 1) {
                LeagueSyncSelection(tournament = null, skippedDueToMultipleTriggers = tournamentsForLeague)
            } else {
                LeagueSyncSelection(tournament = tournamentsForLeague.first())
            }
        }
    }

    private fun isInSyncWindow(tournament: Tournament, today: LocalDate): Boolean {
        val windowStart = tournament.startDate.minusDays(7)
        return !today.isBefore(windowStart) && !today.isAfter(tournament.endDate)
    }
}
