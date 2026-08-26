package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "tournaments")
class Tournament(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    var tournamentName: String,
    var startDate: LocalDate,
    var endDate: LocalDate,
    var tournamentApiId: String,
    @ManyToOne @JoinColumn(name = "league_id")
    var league: League,
)