package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.ZonedDateTime

interface MatchRepository : JpaRepository<Match, Long> {
    fun findByMatchApiId(matchApiId: String): Match?
    fun findByMatchState(matchState: MatchState): List<Match>

    /**
     * Half-open range query: start inclusive, end exclusive — `[start, end)`.
     * Deliberately NOT `Between` (inclusive on both ends), which would double-count
     * a match starting exactly on a shared day boundary in two adjacent ranges.
     * Fetch-joins tournament and league so MatchResponse can read the league name
     * without an N+1 query per match.
     */
    @Query(
        """
        SELECT m FROM Match m
        JOIN FETCH m.tournament t
        JOIN FETCH t.league
        WHERE m.startTime >= :start AND m.startTime < :end
        """,
    )
    fun findByStartTimeGreaterThanEqualAndStartTimeLessThan(
        start: ZonedDateTime,
        end: ZonedDateTime,
    ): List<Match>
}
