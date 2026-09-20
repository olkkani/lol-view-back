package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubExternalId
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import org.springframework.data.jpa.repository.JpaRepository

interface ClubExternalIdRepository : JpaRepository<ClubExternalId, Long> {
    fun findByEntityIdAndProvider(entityId: Long, provider: Provider): ClubExternalId?
    fun findByEntityIdInAndProvider(entityIds: List<Long>, provider: Provider): List<ClubExternalId>
}
