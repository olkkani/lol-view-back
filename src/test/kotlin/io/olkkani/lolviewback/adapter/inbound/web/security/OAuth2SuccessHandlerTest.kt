package io.olkkani.lolviewback.adapter.inbound.web.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.inbound.security.OAuth2SuccessHandler
import io.olkkani.lolviewback.application.auth.AuthProvider
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.auth.ResolveIdentityService
import io.olkkani.lolviewback.application.auth.ResolveResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

class OAuth2SuccessHandlerTest {

    private val resolveIdentityService = mockk<ResolveIdentityService>()
    private val jwtService = mockk<JwtService>()
    private val handler = OAuth2SuccessHandler(resolveIdentityService, jwtService)

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
    fun `on successful Google login writes an issued JWT to the response body`() {
        every {
            resolveIdentityService.resolveIdentity(AuthProvider.GOOGLE, "google-sub-1", null)
        } returns ResolveResult.NewUser(userId = 1L)
        every { jwtService.issueToken(1L) } returns "issued-jwt-token"

        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val writer = StringWriter()
        every { response.writer } returns PrintWriter(writer)

        handler.onAuthenticationSuccess(request, response, oidcAuthentication("google-sub-1"))

        verify { response.contentType = "application/json" }
        assertEquals("""{"token":"issued-jwt-token"}""", writer.toString())
    }
}
