package io.olkkani.lolviewback.adapter.outbound.persistence.dao

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import org.jooq.DSLContext
import org.jooq.generated.Tables
import org.springframework.stereotype.Repository

@Repository
class TournamentDao(
    private val dsl: DSLContext,
) {
    private val externalIdA = Tables.TOURNAMENT_EXTERNAL_IDS.`as`("a")
    private val externalIdB = Tables.TOURNAMENT_EXTERNAL_IDS.`as`("b")

    fun findTournamentIdsWithBothProviders(
        providerA: Provider,
        providerB: Provider,
    ): List<Long> =
        dsl.selectDistinct(externalIdA.ENTITY_ID)
            .from(externalIdA)
            .where(externalIdA.PROVIDER.eq(providerA.name))
            .andExists(
                dsl.selectOne()
                    .from(externalIdB)
                    .where(externalIdB.PROVIDER.eq(providerB.name))
                    .and(externalIdB.ENTITY_ID.eq(externalIdA.ENTITY_ID)),
            )
            .orderBy(externalIdA.ENTITY_ID)
            .fetch(externalIdA.ENTITY_ID)
}
