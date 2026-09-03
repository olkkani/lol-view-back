package io.olkkani.lolviewback.application.auth

import io.olkkani.lolviewback.adapter.outbound.persistence.RefreshTokenRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.RefreshToken
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

@Service
class RefreshTokenService(
    private val repository: RefreshTokenRepository,
    @Value("\${jwt.refresh-expiration-days}") private val refreshExpirationDays: Long,
    @Value("\${jwt.refresh-grace-period-seconds}") private val gracePeriodSeconds: Long,
) {
    private val secureRandom = SecureRandom()

    fun issue(userId: Long): String {
        val rawToken = generateRawToken()
        repository.save(
            RefreshToken(
                userId = userId,
                tokenHash = hash(rawToken),
                expiresAt = LocalDateTime.now().plusDays(refreshExpirationDays),
                createdAt = LocalDateTime.now(),
            ),
        )
        return rawToken
    }

    @Transactional
    fun rotate(rawToken: String): RotateResult {
        val existing = repository.findByTokenHashForUpdate(hash(rawToken)) ?: return RotateResult.NotFound

        if (existing.revokedAt == null) {
            existing.revokedAt = LocalDateTime.now()
            repository.save(existing)
            val newRawToken = issue(existing.userId)
            return RotateResult.Rotated(existing.userId, newRawToken)
        }

        val secondsSinceRevoke = java.time.Duration.between(existing.revokedAt, LocalDateTime.now()).seconds
        if (secondsSinceRevoke <= gracePeriodSeconds) {
            return RotateResult.GracePeriodReuse(existing.userId, generateRawToken())
        }

        repository.revokeAllForUser(existing.userId, LocalDateTime.now())
        return RotateResult.TheftDetected
    }

    fun revoke(rawToken: String) {
        val existing = repository.findByTokenHash(hash(rawToken)) ?: return
        existing.revokedAt = LocalDateTime.now()
        repository.save(existing)
    }

    fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
