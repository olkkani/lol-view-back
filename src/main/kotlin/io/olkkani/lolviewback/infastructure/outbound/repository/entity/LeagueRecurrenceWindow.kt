package io.olkkani.lolviewback.infastructure.outbound.repository.entity

import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.LocalDate

@Entity
class LeagueRecurrenceWindow(
    @Id @Tsid
    var id: Long? = null,
    var label: String,
    var sequenceOrder: Int,
    var intervalYear: Int,
    var lastKnownStartDate: LocalDate,

    @ManyToOne @JoinColumn(name = "league_id")
    var league: League,
) {

}