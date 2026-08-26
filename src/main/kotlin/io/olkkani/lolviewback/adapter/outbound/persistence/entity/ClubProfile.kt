package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "club_profiles")
class ClubProfile(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    var clubName: String,
    var abbreviation: String,
    var logoUrl: String,
    var effectiveFrom: LocalDate,
    var effectiveTo: LocalDate,
    @ManyToOne @JoinColumn(name = "club_id")
    var club: Club? = null,

    @Enumerated(EnumType.STRING)
    var logoBackdrop: LogoBackdrop? = null,
)
