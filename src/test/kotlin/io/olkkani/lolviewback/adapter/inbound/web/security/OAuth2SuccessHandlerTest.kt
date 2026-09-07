package io.olkkani.lolviewback.adapter.inbound.web.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.inbound.security.OAuth2SuccessHandler
import io.olkkani.lolviewback.adapter.inbound.security.PostLoginRedirectAuthorizationRequestResolver
import io.olkkani.lolviewback.adapter.outbound.persistence.UserRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Role
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.User
import io.olkkani.lolviewback.application.auth.AuthProvider
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.auth.RefreshTokenService
import io.olkkani.lolviewback.application.auth.ResolveIdentityService
import io.olkkani.lolviewback.application.auth.ResolveResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.Optional

class OAuth2SuccessHandlerTest(
) {
    private val resolveIdentityService = mockk<ResolveIdentityService>()
    private val jwtService = mockk<JwtService>()
    private val refreshTokenService = mockk<RefreshTokenService>()
    private val userRepository = mockk<UserRepository>()
    private val handler =
        OAuth2SuccessHandler(
            resolveIdentityService,
            jwtService,
            refreshTokenService,
            userRepository,
            accessExpirationMinutes = 30L,
            refreshExpirationDays = 14L,
            frontendUrl = "https://frontend.example",
        )

    private fun encodedState(redirectPath: String?): String {
        val base = "csrf-state-token"
        if (redirectPath == null) return base
        val encoded =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(redirectPath.toByteArray(StandardCharsets.UTF_8))
        return "$base${PostLoginRedirectAuthorizationRequestResolver.STATE_DELIMITER}$encoded"
    }

    private fun oidcAuthentication(sub: String): OAuth2AuthenticationToken {
        val idToken =
            OidcIdToken(
                "id-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                mapOf("sub" to sub, "iss" to "https://accounts.google.com"),
            )
        val oidcUser: OidcUser =
            DefaultOidcUser(
                listOf(OAuth2UserAuthority(mapOf("sub" to sub))),
                idToken,
            )
        return OAuth2AuthenticationToken(oidcUser, oidcUser.authorities, "google")
    }

    @Test
    fun `on successful Google login sets access and refresh token cookies`() {
        every {
            resolveIdentityService.resolveIdentity(AuthProvider.GOOGLE, "google-sub-1", null)
        } returns ResolveResult.NewUser(userId = 1L)
        every { userRepository.findById(1L) } returns Optional.of(User(id = 1L, role = Role.USER))
        every { jwtService.issueToken(1L, Role.USER) } returns "issued-access-jwt"
        every { refreshTokenService.issue(1L) } returns "issued-refresh-token"

        val request = mockk<HttpServletRequest>()
        every { request.getParameter("state") } returns encodedState(null)
        every { request.getSession(false) } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)
        val cookieHeaders = mutableListOf<String>()
        every { response.addHeader("Set-Cookie", capture(cookieHeaders)) } returns Unit

        handler.onAuthenticationSuccess(request, response, oidcAuthentication("google-sub-1"))

        assertTrue(cookieHeaders.any { it.startsWith("access_token=issued-access-jwt") })
        assertTrue(cookieHeaders.any { it.startsWith("refresh_token=issued-refresh-token") })
        verify { response.sendRedirect(any()) }
    }

    @Test
    fun `redirects to frontend root when state carries no redirect path`() {
        every {
            resolveIdentityService.resolveIdentity(AuthProvider.GOOGLE, "google-sub-1", null)
        } returns ResolveResult.NewUser(userId = 1L)
        every { userRepository.findById(1L) } returns Optional.of(User(id = 1L, role = Role.USER))
        every { jwtService.issueToken(1L, Role.USER) } returns "issued-access-jwt"
        every { refreshTokenService.issue(1L) } returns "issued-refresh-token"

        val request = mockk<HttpServletRequest>()
        every { request.getParameter("state") } returns encodedState(null)
        every { request.getSession(false) } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        handler.onAuthenticationSuccess(request, response, oidcAuthentication("google-sub-1"))

        verify { response.sendRedirect("https://frontend.example/") }
    }

    @Test
    fun `redirects to the intercepted path encoded in state`() {
        every {
            resolveIdentityService.resolveIdentity(AuthProvider.GOOGLE, "google-sub-1", null)
        } returns ResolveResult.NewUser(userId = 1L)
        every { userRepository.findById(1L) } returns Optional.of(User(id = 1L, role = Role.USER))
        every { jwtService.issueToken(1L, Role.USER) } returns "issued-access-jwt"
        every { refreshTokenService.issue(1L) } returns "issued-refresh-token"

        val request = mockk<HttpServletRequest>()
        every { request.getParameter("state") } returns encodedState("/teams/42")
        every { request.getSession(false) } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        handler.onAuthenticationSuccess(request, response, oidcAuthentication("google-sub-1"))

        verify { response.sendRedirect("https://frontend.example/teams/42") }
    }

    @Test
    fun `falls back to frontend root when the encoded redirect path is an open-redirect attempt`() {
        every {
            resolveIdentityService.resolveIdentity(AuthProvider.GOOGLE, "google-sub-1", null)
        } returns ResolveResult.NewUser(userId = 1L)
        every { userRepository.findById(1L) } returns Optional.of(User(id = 1L, role = Role.USER))
        every { jwtService.issueToken(1L, Role.USER) } returns "issued-access-jwt"
        every { refreshTokenService.issue(1L) } returns "issued-refresh-token"

        val request = mockk<HttpServletRequest>()
        every { request.getParameter("state") } returns encodedState("//evil.example.com/phish")
        every { request.getSession(false) } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        handler.onAuthenticationSuccess(request, response, oidcAuthentication("google-sub-1"))

        verify { response.sendRedirect("https://frontend.example/") }
    }

    @Test
    fun `role is always read from the database, never from the OIDC claims`() {
        // An OIDC principal can carry arbitrary claims from the provider; role must never be trusted from there.
        every {
            resolveIdentityService.resolveIdentity(AuthProvider.GOOGLE, "google-sub-admin", null)
        } returns ResolveResult.NewUser(userId = 99L)
        every { userRepository.findById(99L) } returns Optional.of(User(id = 99L, role = Role.ADMIN))
        every { jwtService.issueToken(99L, Role.ADMIN) } returns "issued-access-jwt"
        every { refreshTokenService.issue(99L) } returns "issued-refresh-token"

        val request = mockk<HttpServletRequest>()
        every { request.getParameter("state") } returns encodedState(null)
        every { request.getSession(false) } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        handler.onAuthenticationSuccess(request, response, oidcAuthentication("google-sub-admin"))

        verify(exactly = 1) { jwtService.issueToken(99L, Role.ADMIN) }
        verify(exactly = 0) { jwtService.issueToken(99L, Role.USER) }
    }
}
