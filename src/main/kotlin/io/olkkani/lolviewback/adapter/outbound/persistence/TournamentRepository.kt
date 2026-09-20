package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TournamentRepository : JpaRepository<Tournament, Long> {
    @Query(
        """
        SELECT a.entityId FROM TournamentExternalId a
        WHERE a.provider = :providerA
        AND a.entityId IN (
            SELECT b.entityId FROM TournamentExternalId b WHERE b.provider = :providerB
        )
        """,
    )
    fun findTournamentIdsWithBothProviders(
        @Param("providerA") providerA: Provider,
        @Param("providerB") providerB: Provider,
    ): List<Long>
}
