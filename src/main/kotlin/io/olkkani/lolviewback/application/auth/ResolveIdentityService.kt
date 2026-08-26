package io.olkkani.lolviewback.application.auth

import io.olkkani.lolviewback.adapter.outbound.persistence.UserIdentityRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.UserRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.User
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.UserIdentity
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ResolveIdentityService(
    private val userIdentityRepository: UserIdentityRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun resolveIdentity(
        provider: AuthProvider,
        providerUserId: String,
        currentSessionUserId: Long?,
    ): ResolveResult {
        val existing = userIdentityRepository.findByProviderAndProviderUserId(provider.name, providerUserId)

        if (currentSessionUserId == null) {
            if (existing == null) {
                val newUser = userRepository.save(User())
                saveIdentityOrThrow(newUser.id, provider, providerUserId)
                return ResolveResult.NewUser(newUser.id)
            }
            return ResolveResult.LoggedIn(existing.userId)
        }

        if (existing == null) {
            saveIdentityOrThrow(currentSessionUserId, provider, providerUserId)
            return ResolveResult.Linked(currentSessionUserId)
        }

        if (existing.userId != currentSessionUserId) {
            return ResolveResult.AlreadyLinkedElsewhere
        }

        return ResolveResult.LoggedIn(currentSessionUserId)
    }

    private fun saveIdentityOrThrow(userId: Long, provider: AuthProvider, providerUserId: String) {
        try {
            userIdentityRepository.saveAndFlush(
                UserIdentity(userId = userId, provider = provider.name, providerUserId = providerUserId),
            )
        } catch (ex: DataIntegrityViolationException) {
            throw IdentityAlreadyLinkedException()
        }
    }
}
