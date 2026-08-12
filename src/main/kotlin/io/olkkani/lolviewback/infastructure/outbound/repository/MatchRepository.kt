package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import org.springframework.data.jpa.repository.JpaRepository
import java.time.ZonedDateTime

interface MatchRepository : JpaRepository<Match, Long> {
    fun findByMatchApiId(matchApiId: String): Match?
    fun findByMatchState(matchState: MatchState): List<Match>
    fun findByStartTimeBetween(start: ZonedDateTime, end: ZonedDateTime): List<Match>
}
