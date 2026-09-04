package io.olkkani.lolviewback.adapter.inbound.security

import io.olkkani.lolviewback.application.auth.AuthProvider
import io.olkkani.lolviewback.application.auth.IdentityAlreadyLinkedException
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.auth.RefreshTokenService
import io.olkkani.lolviewback.application.auth.ResolveIdentityService
import io.olkkani.lolviewback.application.auth.ResolveResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2SuccessHandler(
    private val resolveIdentityService: ResolveIdentityService,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    @Value("\${jwt.access-expiration-minutes}") private val accessExpirationMinutes: Long,
    @Value("\${jwt.refresh-expiration-days}") private val refreshExpirationDays: Long,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oauthToken = authentication as OAuth2AuthenticationToken
        val oidcUser = oauthToken.principal as OidcUser
        val providerUserId = requireNotNull(oidcUser.subject)

        val result = try {
            resolveIdentityService.resolveIdentity(AuthProvider.GOOGLE, providerUserId, currentSessionUserId = null)
        } catch (ex: IdentityAlreadyLinkedException) {
            response.status = HttpServletResponse.SC_CONFLICT
            return
        }

        val userId = when (result) {
            is ResolveResult.NewUser -> result.userId
            is ResolveResult.LoggedIn -> result.userId
            is ResolveResult.Linked -> result.userId
            ResolveResult.AlreadyLinkedElsewhere -> {
                response.status = HttpServletResponse.SC_CONFLICT
                return
            }
        }

        val accessToken = jwtService.issueToken(userId)
        val refreshToken = refreshTokenService.issue(userId)

        response.addHeader(
            "Set-Cookie",
            CookieSupport.buildAccessTokenCookie(accessToken, accessExpirationMinutes * 60).toString(),
        )
        response.addHeader(
            "Set-Cookie",
            CookieSupport.buildRefreshTokenCookie(refreshToken, refreshExpirationDays * 86_400).toString(),
        )
        // Placeholder redirect target — no frontend callback URL exists in this codebase yet.
        // Replace with the real post-login destination (or make it configurable via
        // a property) once the frontend integration path is confirmed.
        response.sendRedirect("/")
    }
}
