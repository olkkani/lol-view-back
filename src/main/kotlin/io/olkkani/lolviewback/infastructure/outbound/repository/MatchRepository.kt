package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import org.springframework.data.jpa.repository.JpaRepository
import java.time.ZonedDateTime

interface MatchRepository : JpaRepository<Match, Long> {
    fun findByMatchApiId(matchApiId: String): Match?
    fun findByMatchState(matchState: MatchState): List<Match>

    /**
     * Half-open range query: start inclusive, end exclusive — `[start, end)`.
     * Deliberately NOT `Between` (inclusive on both ends), which would double-count
     * a match starting exactly on a shared day boundary in two adjacent ranges.
     */
    fun findByStartTimeGreaterThanEqualAndStartTimeLessThan(
        start: ZonedDateTime,
        end: ZonedDateTime,
    ): List<Match>
}
