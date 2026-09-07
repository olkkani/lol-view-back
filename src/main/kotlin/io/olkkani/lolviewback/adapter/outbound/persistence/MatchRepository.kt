package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import org.springframework.data.domain.Pageable
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

    fun findAllByMatchState(matchState: MatchState): List<Match>

    /**
     * Head-to-head lookup: both clubId1 and clubId2 must have a MatchParticipant
     * row on the same match. Deliberately two EXISTS clauses (not a single IN)
     * so the two clauses are ANDed — a match where only one club played is
     * excluded, not silently matched.
     */
    @Query(
        """
        SELECT m FROM Match m
        WHERE m.matchState = io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState.COMPLETED
        AND m.startTime < :beforeStartTime
        AND EXISTS (SELECT 1 FROM MatchParticipant p1 WHERE p1.match = m AND p1.club.id = :clubId1)
        AND EXISTS (SELECT 1 FROM MatchParticipant p2 WHERE p2.match = m AND p2.club.id = :clubId2)
        ORDER BY m.startTime DESC
        """,
    )
    fun findHeadToHeadBefore(
        clubId1: Long,
        clubId2: Long,
        beforeStartTime: ZonedDateTime,
        pageable: Pageable,
    ): List<Match>
}
