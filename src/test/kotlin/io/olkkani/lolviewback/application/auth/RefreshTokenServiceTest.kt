package io.olkkani.lolviewback.application.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.persistence.RefreshTokenRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.RefreshToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RefreshTokenServiceTest {

    private val repository = mockk<RefreshTokenRepository>()
    private val service = RefreshTokenService(
        repository = repository,
        refreshExpirationDays = 14,
        gracePeriodSeconds = 20,
    )

    @Test
    fun `issue saves a hashed token and returns the raw token`() {
        val savedSlot = slot<RefreshToken>()
        every { repository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val rawToken = service.issue(userId = 42L)

        assertTrue(rawToken.isNotBlank())
        assertEquals(42L, savedSlot.captured.userId)
        assertTrue(savedSlot.captured.tokenHash != rawToken)
    }

    @Test
    fun `rotate returns NotFound when the token hash has no matching row`() {
        every { repository.findByTokenHashForUpdate(any()) } returns null

        val result = service.rotate("unknown-raw-token")

        assertEquals(RotateResult.NotFound, result)
    }

    @Test
    fun `rotate on a fresh non-revoked token revokes it and issues a new pair`() {
        val existing = RefreshToken(
            userId = 42L,
            tokenHash = service.hash("valid-raw-token"),
            expiresAt = LocalDateTime.now().plusDays(14),
            createdAt = LocalDateTime.now(),
            revokedAt = null,
        )
        every { repository.findByTokenHashForUpdate(any()) } returns existing
        val savedSlot = slot<RefreshToken>()
        every { repository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val result = service.rotate("valid-raw-token")

        assertTrue(result is RotateResult.Rotated)
        assertEquals(42L, (result as RotateResult.Rotated).userId)
        verify { repository.save(existing) }
        assertTrue(existing.revokedAt != null)
    }

    @Test
    fun `rotate on a token revoked within the grace period reissues without treating it as theft`() {
        val existing = RefreshToken(
            userId = 42L,
            tokenHash = service.hash("reused-raw-token"),
            expiresAt = LocalDateTime.now().plusDays(14),
            createdAt = LocalDateTime.now().minusSeconds(30),
            revokedAt = LocalDateTime.now().minusSeconds(5),
        )
        every { repository.findByTokenHashForUpdate(any()) } returns existing

        val result = service.rotate("reused-raw-token")

        assertTrue(result is RotateResult.GracePeriodReuse)
        assertEquals(42L, (result as RotateResult.GracePeriodReuse).userId)
    }

    @Test
    fun `rotate on a token revoked past the grace period is treated as theft and revokes the whole user`() {
        val existing = RefreshToken(
            userId = 42L,
            tokenHash = service.hash("stolen-raw-token"),
            expiresAt = LocalDateTime.now().plusDays(14),
            createdAt = LocalDateTime.now().minusDays(1),
            revokedAt = LocalDateTime.now().minusSeconds(60),
        )
        every { repository.findByTokenHashForUpdate(any()) } returns existing
        every { repository.revokeAllForUser(42L, any()) } returns 3

        val result = service.rotate("stolen-raw-token")

        assertEquals(RotateResult.TheftDetected, result)
        verify { repository.revokeAllForUser(42L, any()) }
    }

    @Test
    fun `revoke marks the matching row as revoked`() {
        val existing = RefreshToken(
            userId = 42L,
            tokenHash = service.hash("logout-raw-token"),
            expiresAt = LocalDateTime.now().plusDays(14),
            createdAt = LocalDateTime.now(),
        )
        every { repository.findByTokenHash(any()) } returns existing
        every { repository.save(any()) } returns existing

        service.revoke("logout-raw-token")

        assertTrue(existing.revokedAt != null)
    }
}
