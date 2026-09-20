package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider

class MissingExternalIdException(
    entityType: String,
    entityId: Long,
    provider: Provider,
) : RuntimeException("No $provider mapping exists for $entityType id=$entityId")
