package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
abstract class ExternalId(
    @Id
    var id: Long,
    @Enumerated(EnumType.STRING)
    var provider: Provider,
    var externalId: String,
)
