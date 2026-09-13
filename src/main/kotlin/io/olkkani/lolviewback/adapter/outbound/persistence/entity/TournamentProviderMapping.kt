package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "tournament_provider_mappings")
class TournamentProviderMapping(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    var lolesportsTournamentId: String,
    var pandascoreTournamentId: String,
)
