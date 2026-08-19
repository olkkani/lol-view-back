package io.olkkani.lolviewback.infastructure.outbound.repository.entity

import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "club_profiles")
class ClubProfile(
    @Id @Tsid
    var id: Long? = null,
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
