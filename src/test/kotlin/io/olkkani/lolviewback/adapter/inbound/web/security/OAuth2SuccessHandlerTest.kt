package io.olkkani.lolviewback.adapter.inbound.web.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.inbound.security.OAuth2SuccessHandler
import io.olkkani.lolviewback.application.auth.AuthProvider
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.auth.RefreshTokenService
import io.olkkani.lolviewback.application.auth.ResolveIdentityService
import io.olkkani.lolviewback.application.auth.ResolveResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import java.time.Instant

class OAuth2SuccessHandlerTest {

    private val resolveIdentityService = mockk<ResolveIdentityService>()
    private val jwtService = mockk<JwtService>()
    private val refreshTokenService = mockk<RefreshTokenService>()
    private val handler = OAuth2SuccessHandler(
        resolveIdentityService,
        jwtService,
        refreshTokenService,
        accessExpirationMinutes = 30L,
        refreshExpirationDays = 14L,
    )

    private fun oidcAuthentication(sub: String): OAuth2AuthenticationToken {
        val idToken = OidcIdToken(
            "id-token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            mapOf("sub" to sub, "iss" to "https://accounts.google.com"),
        )
        val oidcUser: OidcUser = DefaultOidcUser(
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
        every { jwtService.issueToken(1L) } returns "issued-access-jwt"
        every { refreshTokenService.issue(1L) } returns "issued-refresh-token"

        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val cookieHeaders = mutableListOf<String>()
        every { response.addHeader("Set-Cookie", capture(cookieHeaders)) } returns Unit

        handler.onAuthenticationSuccess(request, response, oidcAuthentication("google-sub-1"))

        assertTrue(cookieHeaders.any { it.startsWith("access_token=issued-access-jwt") })
        assertTrue(cookieHeaders.any { it.startsWith("refresh_token=issued-refresh-token") })
        verify { response.sendRedirect(any()) }
    }
}
