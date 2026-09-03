package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    @Column(name = "user_id")
    var userId: Long,
    @Column(name = "token_hash")
    var tokenHash: String,
    @Column(name = "expires_at")
    var expiresAt: LocalDateTime,
    @Column(name = "created_at")
    var createdAt: LocalDateTime,
    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,
)
