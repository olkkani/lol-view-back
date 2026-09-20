package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueExternalId
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DataJpaTest
class LeagueExternalIdRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var repository: LeagueExternalIdRepository

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `finds a mapping by entity id and provider`() {
        // Entity ids below use the 2000s range to avoid colliding with the 1001-1006
        // leagues seeded in V1.1 and already backfilled into league_external_ids by
        // Migration A (V1.2) — those seeded rows already own the (entityId, LOLESPORTS) pair.
        repository.save(LeagueExternalId(entityId = 2004L, provider = Provider.LOLESPORTS, externalId = "98767991310872058"))
        entityManager.flush()
        entityManager.clear()

        val found = repository.findByEntityIdAndProvider(2004L, Provider.LOLESPORTS)

        assertEquals("98767991310872058", found?.externalId)
    }

    @Test
    fun `returns null when no mapping exists for the given entity id and provider`() {
        val found = repository.findByEntityIdAndProvider(9999L, Provider.LOLESPORTS)

        assertNull(found)
    }

    @Test
    fun `finds multiple mappings by entity id list and provider in one batch query`() {
        repository.save(LeagueExternalId(entityId = 2001L, provider = Provider.LOLESPORTS, externalId = "a"))
        repository.save(LeagueExternalId(entityId = 2002L, provider = Provider.LOLESPORTS, externalId = "b"))
        repository.save(LeagueExternalId(entityId = 2003L, provider = Provider.PANDASCORE, externalId = "c"))
        entityManager.flush()
        entityManager.clear()

        val found = repository.findByEntityIdInAndProvider(listOf(2001L, 2002L, 2003L), Provider.LOLESPORTS)

        assertEquals(setOf(2001L, 2002L), found.map { it.entityId }.toSet())
    }

    @Test
    fun `rejects a duplicate entity id and provider pair`() {
        repository.save(LeagueExternalId(entityId = 2004L, provider = Provider.LOLESPORTS, externalId = "first"))
        entityManager.flush()

        org.junit.jupiter.api.assertThrows<DataIntegrityViolationException> {
            repository.saveAndFlush(LeagueExternalId(entityId = 2004L, provider = Provider.LOLESPORTS, externalId = "second"))
        }
    }
}
