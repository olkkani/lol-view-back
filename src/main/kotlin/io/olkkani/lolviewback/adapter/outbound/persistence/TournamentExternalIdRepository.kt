package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.TournamentExternalId
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentExternalIdRepository : JpaRepository<TournamentExternalId, Long> {
    fun findByEntityIdAndProvider(entityId: Long, provider: Provider): TournamentExternalId?
    fun findByEntityIdInAndProvider(entityIds: List<Long>, provider: Provider): List<TournamentExternalId>
}
