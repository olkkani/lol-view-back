package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.User
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.UserIdentity
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
class UserIdentityRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var userIdentityRepository: UserIdentityRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `findByProviderAndProviderUserId returns the matching identity`() {
        val user = userRepository.save(User(id = 1L))
        entityManager.flush()
        val saved = userIdentityRepository.save(
            UserIdentity(userId = user.id, provider = "GOOGLE", providerUserId = "sub-123"),
        )
        entityManager.flush()
        entityManager.clear()

        val found = userIdentityRepository.findByProviderAndProviderUserId("GOOGLE", "sub-123")

        assertEquals(saved.id, found?.id)
        assertEquals(user.id, found?.userId)
    }

    @Test
    fun `findByProviderAndProviderUserId returns null when no identity matches`() {
        val found = userIdentityRepository.findByProviderAndProviderUserId("GOOGLE", "nonexistent")

        assertNull(found)
    }

    @Test
    fun `findByUserId returns all identities linked to a user`() {
        val user = userRepository.save(User(id = 2L))
        entityManager.flush()
        userIdentityRepository.save(UserIdentity(userId = user.id, provider = "GOOGLE", providerUserId = "sub-456"))
        entityManager.flush()
        entityManager.clear()

        val found = userIdentityRepository.findByUserId(user.id)

        assertEquals(1, found.size)
        assertEquals("GOOGLE", found[0].provider)
    }
}
