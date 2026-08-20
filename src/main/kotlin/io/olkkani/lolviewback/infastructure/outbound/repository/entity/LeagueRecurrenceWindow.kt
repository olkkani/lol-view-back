package io.olkkani.lolviewback.infastructure.outbound.repository.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "league_recurrence_windows")
class LeagueRecurrenceWindow(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    var label: String,
    var sequenceOrder: Int,
    var intervalYear: Int,
    var startDate: LocalDate,

    @ManyToOne @JoinColumn(name = "league_id")
    var league: League,
)
