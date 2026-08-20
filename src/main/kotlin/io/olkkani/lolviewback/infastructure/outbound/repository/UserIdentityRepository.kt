package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.UserIdentity
import org.springframework.data.jpa.repository.JpaRepository

interface UserIdentityRepository : JpaRepository<UserIdentity, Long> {
    fun findByProviderAndProviderUserId(provider: String, providerUserId: String): UserIdentity?
    fun findByUserId(userId: Long): List<UserIdentity>
}
