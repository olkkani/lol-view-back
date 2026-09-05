package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Role
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.User
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DataJpaTest
class UserRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `a newly constructed user defaults to USER role`() {
        val saved = userRepository.save(User())
        entityManager.flush()
        entityManager.clear()

        val reloaded = userRepository.findById(saved.id).orElseThrow()

        assertEquals(Role.USER, reloaded.role)
    }

    @Test
    fun `role persists and reloads as ADMIN`() {
        val user = User(role = Role.ADMIN)
        val saved = userRepository.save(user)
        entityManager.flush()
        entityManager.clear()

        val reloaded = userRepository.findById(saved.id).orElseThrow()

        assertEquals(Role.ADMIN, reloaded.role)
    }

    @Test
    fun `role is stored as string in database`() {
        val user = User(role = Role.ADMIN)
        val saved = userRepository.save(user)
        entityManager.flush()
        entityManager.clear()

        val query: Query = entityManager.createNativeQuery(
            "SELECT role FROM users WHERE id = :id",
        )
        query.setParameter("id", saved.id)

        assertEquals("ADMIN", query.singleResult)
    }
}
