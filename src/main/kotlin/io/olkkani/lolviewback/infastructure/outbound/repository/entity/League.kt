package io.olkkani.lolviewback.infastructure.outbound.repository.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "leagues")
class League(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    var leagueName: String,
    var logoUrl: String,
    var isActive: Boolean,
    var leagueApiId: String,

    @Enumerated(EnumType.STRING)
    var logoBackdrop: LogoBackdrop? = null,
)