package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.TournamentProviderMapping
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DataJpaTest
class TournamentProviderMappingRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var repository: TournamentProviderMappingRepository

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `finds a mapping by lolesports tournament id`() {
        repository.save(
            TournamentProviderMapping(
                lolesportsTournamentId = "115548147890329817",
                pandascoreTournamentId = "21722",
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val found = repository.findByLolesportsTournamentId("115548147890329817")

        assertEquals("21722", found?.pandascoreTournamentId)
    }

    @Test
    fun `returns null when no mapping exists for the given lolesports tournament id`() {
        val found = repository.findByLolesportsTournamentId("does-not-exist")

        assertNull(found)
    }
}
