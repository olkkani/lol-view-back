package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.TournamentProviderMapping
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentProviderMappingRepository : JpaRepository<TournamentProviderMapping, Long> {
    fun findByLolesportsTournamentId(lolesportsTournamentId: String): TournamentProviderMapping?
}
