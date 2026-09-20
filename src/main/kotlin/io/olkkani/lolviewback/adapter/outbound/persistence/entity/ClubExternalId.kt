package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "club_external_ids")
class ClubExternalId(
    id: Long = TSID.Factory.getTsid().toLong(),
    var entityId: Long,
    provider: Provider,
    externalId: String,
) : ExternalId(id, provider, externalId)
