package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.RefreshToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :tokenHash")
    fun findByTokenHashForUpdate(@Param("tokenHash") tokenHash: String): RefreshToken?

    @Modifying
    @Transactional
    @Query(
        "update RefreshToken r set r.revokedAt = :revokedAt " +
            "where r.userId = :userId and r.revokedAt is null",
    )
    fun revokeAllForUser(@Param("userId") userId: Long, @Param("revokedAt") revokedAt: LocalDateTime): Int

    @Modifying
    @Transactional
    @Query("delete from RefreshToken r where r.expiresAt < :cutoff")
    fun deleteAllExpiredBefore(@Param("cutoff") cutoff: LocalDateTime): Int
}
