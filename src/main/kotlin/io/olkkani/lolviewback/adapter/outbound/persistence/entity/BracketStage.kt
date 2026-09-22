package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "bracket_stages")
class BracketStage(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    var tournamentId: String,
    var name: String,
    var format: String,
    var ordinal: Int,
    var config: String?,
)
