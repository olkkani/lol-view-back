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
    var isActive: Boolean,
    var leagueApiId: String,
    var leagueCycle: Enum<LeagueCycle>
)

enum class LeagueCycle {
    MULTI_SPLIT,
    ANNUAL,
    QUADRENNIAL,
}