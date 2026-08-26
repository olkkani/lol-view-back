package io.olkkani.lolviewback.domain.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.persistence.UserIdentityRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.UserRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.User
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.UserIdentity
import io.olkkani.lolviewback.application.auth.AuthProvider
import io.olkkani.lolviewback.application.auth.IdentityAlreadyLinkedException
import io.olkkani.lolviewback.application.auth.ResolveIdentityService
import io.olkkani.lolviewback.application.auth.ResolveResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

class ResolveIdentityServiceTest {

    private val userIdentityRepository = mockk<UserIdentityRepository>()
    private val userRepository = mockk<UserRepository>()
    private val service = ResolveIdentityService(userIdentityRepository, userRepository)

    @Test
    fun `no session and no existing identity creates a new user`() {
        every { userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE.name, "sub-1") } returns null
        val savedUser = User(id = 100L)
        every { userRepository.save(any()) } returns savedUser
        every { userIdentityRepository.saveAndFlush(any()) } returns UserIdentity(id = 1L, userId = 100L, provider = "GOOGLE", providerUserId = "sub-1")

        val result = service.resolveIdentity(AuthProvider.GOOGLE, "sub-1", currentSessionUserId = null)

        assertTrue(result is ResolveResult.NewUser)
        assertEquals(100L, (result as ResolveResult.NewUser).userId)
    }

    @Test
    fun `no session and existing identity logs the user in`() {
        every {
            userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE.name, "sub-2")
        } returns UserIdentity(id = 2L, userId = 200L, provider = "GOOGLE", providerUserId = "sub-2")

        val result = service.resolveIdentity(AuthProvider.GOOGLE, "sub-2", currentSessionUserId = null)

        assertTrue(result is ResolveResult.LoggedIn)
        assertEquals(200L, (result as ResolveResult.LoggedIn).userId)
    }

    @Test
    fun `existing session and no existing identity links the new identity to the session user`() {
        every { userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE.name, "sub-3") } returns null
        val savedIdentity = slot<UserIdentity>()
        every { userIdentityRepository.saveAndFlush(capture(savedIdentity)) } answers { savedIdentity.captured }

        val result = service.resolveIdentity(AuthProvider.GOOGLE, "sub-3", currentSessionUserId = 300L)

        assertTrue(result is ResolveResult.Linked)
        assertEquals(300L, (result as ResolveResult.Linked).userId)
        assertEquals(300L, savedIdentity.captured.userId)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `existing session but identity already linked to a different user is rejected`() {
        every {
            userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE.name, "sub-4")
        } returns UserIdentity(id = 4L, userId = 400L, provider = "GOOGLE", providerUserId = "sub-4")

        val result = service.resolveIdentity(AuthProvider.GOOGLE, "sub-4", currentSessionUserId = 999L)

        assertEquals(ResolveResult.AlreadyLinkedElsewhere, result)
    }

    @Test
    fun `existing session and identity already linked to the same user is a re-login, not a duplicate link`() {
        every {
            userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE.name, "sub-5")
        } returns UserIdentity(id = 5L, userId = 500L, provider = "GOOGLE", providerUserId = "sub-5")

        val result = service.resolveIdentity(AuthProvider.GOOGLE, "sub-5", currentSessionUserId = 500L)

        assertTrue(result is ResolveResult.LoggedIn)
        assertEquals(500L, (result as ResolveResult.LoggedIn).userId)
        verify(exactly = 0) { userIdentityRepository.saveAndFlush(any()) }
    }

    @Test
    fun `a race that slips past the existence check surfaces as IdentityAlreadyLinkedException`() {
        every { userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE.name, "sub-6") } returns null
        val newUser = User(id = 600L)
        every { userRepository.save(any()) } returns newUser
        every { userIdentityRepository.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate key")

        assertThrows(IdentityAlreadyLinkedException::class.java) {
            service.resolveIdentity(AuthProvider.GOOGLE, "sub-6", currentSessionUserId = null)
        }
    }
}
