package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.UserIdentity
import org.springframework.data.jpa.repository.JpaRepository

interface UserIdentityRepository : JpaRepository<UserIdentity, Long> {
    fun findByProviderAndProviderUserId(provider: String, providerUserId: String): UserIdentity?
    fun findByUserId(userId: Long): List<UserIdentity>
}
