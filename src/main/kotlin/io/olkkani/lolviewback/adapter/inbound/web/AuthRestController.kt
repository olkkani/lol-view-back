package io.olkkani.lolviewback.adapter.inbound.web

import io.olkkani.lolviewback.adapter.inbound.web.dto.UserIdentityResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.UserIdentityRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthRestController(
    private val userIdentityRepository: UserIdentityRepository,
) {
    @GetMapping("/me")
    fun getMe(): List<UserIdentityResponse> {
        val userId = SecurityContextHolder.getContext().authentication!!.principal as String
        return userIdentityRepository.findByUserId(userId.toLong()).map {
            UserIdentityResponse(provider = it.provider, providerUserId = it.providerUserId)
        }
    }
}
