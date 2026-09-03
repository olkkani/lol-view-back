package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.RefreshToken
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.User
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
import java.time.LocalDateTime

@Testcontainers
@DataJpaTest
class RefreshTokenRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `findByTokenHashForUpdate returns the matching row`() {
        val user = userRepository.save(User(id = 1L))
        entityManager.flush()
        val saved = refreshTokenRepository.save(
            RefreshToken(
                userId = user.id,
                tokenHash = "hash-1",
                expiresAt = LocalDateTime.now().plusDays(14),
                createdAt = LocalDateTime.now(),
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val found = refreshTokenRepository.findByTokenHashForUpdate("hash-1")

        assertEquals(saved.id, found?.id)
    }

    @Test
    fun `findByTokenHashForUpdate returns null when no row matches`() {
        val found = refreshTokenRepository.findByTokenHashForUpdate("nonexistent")

        assertNull(found)
    }

    @Test
    fun `revokeAllForUser marks every non-revoked row for that user as revoked`() {
        val user = userRepository.save(User(id = 2L))
        entityManager.flush()
        refreshTokenRepository.save(
            RefreshToken(userId = user.id, tokenHash = "hash-a", expiresAt = LocalDateTime.now().plusDays(1), createdAt = LocalDateTime.now()),
        )
        refreshTokenRepository.save(
            RefreshToken(userId = user.id, tokenHash = "hash-b", expiresAt = LocalDateTime.now().plusDays(1), createdAt = LocalDateTime.now()),
        )
        entityManager.flush()
        entityManager.clear()

        val revokedCount = refreshTokenRepository.revokeAllForUser(user.id, LocalDateTime.now())
        entityManager.clear()

        assertEquals(2, revokedCount)
        val row = refreshTokenRepository.findByTokenHash("hash-a")
        assertEquals(true, row?.revokedAt != null)
    }

    @Test
    fun `deleteAllExpiredBefore removes only rows past the cutoff`() {
        val user = userRepository.save(User(id = 3L))
        entityManager.flush()
        refreshTokenRepository.save(
            RefreshToken(userId = user.id, tokenHash = "expired", expiresAt = LocalDateTime.now().minusDays(1), createdAt = LocalDateTime.now().minusDays(15)),
        )
        refreshTokenRepository.save(
            RefreshToken(userId = user.id, tokenHash = "still-valid", expiresAt = LocalDateTime.now().plusDays(1), createdAt = LocalDateTime.now()),
        )
        entityManager.flush()
        entityManager.clear()

        val deletedCount = refreshTokenRepository.deleteAllExpiredBefore(LocalDateTime.now())
        entityManager.clear()

        assertEquals(1, deletedCount)
        assertNull(refreshTokenRepository.findByTokenHash("expired"))
        assertEquals("still-valid", refreshTokenRepository.findByTokenHash("still-valid")?.tokenHash)
    }
}
