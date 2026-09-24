package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueExternalId
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import org.springframework.data.jpa.repository.JpaRepository

interface LeagueExternalIdRepository : JpaRepository<LeagueExternalId, Long> {
    fun findByEntityIdAndProvider(
        entityId: Long,
        provider: Provider,
    ): LeagueExternalId?

    fun findByEntityIdInAndProvider(
        entityIds: List<Long>,
        provider: Provider,
    ): List<LeagueExternalId>

    fun findByEntityId(entityId: Long): List<LeagueExternalId>

    fun findByEntityIdIn(entityIds: List<Long>): List<LeagueExternalId>
}
