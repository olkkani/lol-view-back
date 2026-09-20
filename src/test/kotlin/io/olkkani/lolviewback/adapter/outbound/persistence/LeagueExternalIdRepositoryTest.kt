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
        repository.save(LeagueExternalId(entityId = 1004L, provider = Provider.LOLESPORTS, externalId = "98767991310872058"))
        entityManager.flush()
        entityManager.clear()

        val found = repository.findByEntityIdAndProvider(1004L, Provider.LOLESPORTS)

        assertEquals("98767991310872058", found?.externalId)
    }

    @Test
    fun `returns null when no mapping exists for the given entity id and provider`() {
        val found = repository.findByEntityIdAndProvider(9999L, Provider.LOLESPORTS)

        assertNull(found)
    }

    @Test
    fun `finds multiple mappings by entity id list and provider in one batch query`() {
        repository.save(LeagueExternalId(entityId = 1001L, provider = Provider.LOLESPORTS, externalId = "a"))
        repository.save(LeagueExternalId(entityId = 1002L, provider = Provider.LOLESPORTS, externalId = "b"))
        repository.save(LeagueExternalId(entityId = 1003L, provider = Provider.PANDASCORE, externalId = "c"))
        entityManager.flush()
        entityManager.clear()

        val found = repository.findByEntityIdInAndProvider(listOf(1001L, 1002L, 1003L), Provider.LOLESPORTS)

        assertEquals(setOf(1001L, 1002L), found.map { it.entityId }.toSet())
    }

    @Test
    fun `rejects a duplicate entity id and provider pair`() {
        repository.save(LeagueExternalId(entityId = 1004L, provider = Provider.LOLESPORTS, externalId = "first"))
        entityManager.flush()

        org.junit.jupiter.api.assertThrows<DataIntegrityViolationException> {
            repository.saveAndFlush(LeagueExternalId(entityId = 1004L, provider = Provider.LOLESPORTS, externalId = "second"))
        }
    }
}
