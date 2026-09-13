package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "bracket_matches")
class BracketMatch(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    var tournamentId: String,
    var matchId: Long,
    var name: String,
    var status: String,
    var slug: String?,
    var winnerId: Long?,
    var winnerType: String?,
    var beginAt: LocalDateTime?,
    var endAt: LocalDateTime?,
    var scheduledAt: LocalDateTime?,
    var previousMatches: String,
    var opponents: String,
    var results: String,
)
