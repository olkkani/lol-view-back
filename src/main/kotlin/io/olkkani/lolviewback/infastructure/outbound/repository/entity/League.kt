package io.olkkani.lolviewback.infastructure.outbound.repository.entity

import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "leagues")
class League(
    @Id @Tsid
    var id: Long? = null,
    var leagueName: String,
    var leagueLogoUrl: String,
    var isActive: Boolean,
    var leagueApiId: String,
)