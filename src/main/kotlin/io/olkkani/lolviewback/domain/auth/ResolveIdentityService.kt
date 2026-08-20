package io.olkkani.lolviewback.domain.auth

import io.olkkani.lolviewback.infastructure.outbound.repository.UserIdentityRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.UserRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.User
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.UserIdentity
import org.springframework.stereotype.Service

@Service
class ResolveIdentityService(
    private val userIdentityRepository: UserIdentityRepository,
    private val userRepository: UserRepository,
) {
    fun resolveIdentity(
        provider: AuthProvider,
        providerUserId: String,
        currentSessionUserId: Long?,
    ): ResolveResult {
        val existing = userIdentityRepository.findByProviderAndProviderUserId(provider.name, providerUserId)

        if (currentSessionUserId == null) {
            if (existing == null) {
                val newUser = userRepository.save(User())
                userIdentityRepository.save(
                    UserIdentity(userId = newUser.id, provider = provider.name, providerUserId = providerUserId),
                )
                return ResolveResult.NewUser(newUser.id)
            }
            return ResolveResult.LoggedIn(existing.userId)
        }

        if (existing == null) {
            userIdentityRepository.save(
                UserIdentity(userId = currentSessionUserId, provider = provider.name, providerUserId = providerUserId),
            )
            return ResolveResult.Linked(currentSessionUserId)
        }

        if (existing.userId != currentSessionUserId) {
            return ResolveResult.AlreadyLinkedElsewhere
        }

        return ResolveResult.LoggedIn(currentSessionUserId)
    }
}
