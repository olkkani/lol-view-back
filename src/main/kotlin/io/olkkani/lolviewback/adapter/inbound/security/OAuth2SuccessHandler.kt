package io.olkkani.lolviewback.adapter.inbound.security

import io.olkkani.lolviewback.adapter.outbound.persistence.UserRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Role
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
    private val userRepository: UserRepository,
    @Value("\${jwt.access-expiration-minutes}") private val accessExpirationMinutes: Long,
    @Value("\${jwt.refresh-expiration-days}") private val refreshExpirationDays: Long,
    @Value("\${app.frontend-url}") private val frontendUrl: String,
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oauthToken = authentication as OAuth2AuthenticationToken
        val oidcUser = oauthToken.principal as OidcUser
        val providerUserId = requireNotNull(oidcUser.subject)

        val result =
            try {
                resolveIdentityService.resolveIdentity(AuthProvider.GOOGLE, providerUserId, currentSessionUserId = null)
            } catch (ex: IdentityAlreadyLinkedException) {
                response.status = HttpServletResponse.SC_CONFLICT
                return
            }

        val userId =
            when (result) {
                is ResolveResult.NewUser -> {
                    result.userId
                }

                is ResolveResult.LoggedIn -> {
                    result.userId
                }

                is ResolveResult.Linked -> {
                    result.userId
                }

                ResolveResult.AlreadyLinkedElsewhere -> {
                    response.status = HttpServletResponse.SC_CONFLICT
                    return
                }
            }

        // Role is always read from our own DB, never from oidcUser/oauthToken — an OAuth2
        // provider's claims must never be able to grant elevated access.
        val role = userRepository.findById(userId).map { it.role }.orElse(Role.USER)

        val accessToken = jwtService.issueToken(userId, role)
        val refreshToken = refreshTokenService.issue(userId)

        response.addHeader(
            "Set-Cookie",
            CookieSupport.buildAccessTokenCookie(accessToken, accessExpirationMinutes * 60).toString(),
        )
        response.addHeader(
            "Set-Cookie",
            CookieSupport.buildRefreshTokenCookie(refreshToken, refreshExpirationDays * 86_400).toString(),
        )
        val redirectPath =
            PostLoginRedirectAuthorizationRequestResolver
                .extractRedirectPath(request.getParameter("state"))
                ?.takeIf(::isSafeRedirectPath)
                ?: "/"

        // This API is stateless (JWT cookies carry auth from here on). The only reason a
        // session exists at all is that Spring's OAuth2 login flow stores the authorization
        // request (state/PKCE) in it. Once login succeeds that session has no further purpose,
        // so drop it rather than let it linger for the cookie's lifetime.
        request.getSession(false)?.invalidate()

        response.sendRedirect(frontendUrl + redirectPath)
    }

    // Only a same-origin relative path is allowed. Rejects absolute URLs, protocol-relative
    // "//host" paths, and backslash variants some browsers still treat as "//" — otherwise the
    // `redirect` query param becomes an open-redirect vector, since it round-trips through the
    // OAuth2 state unauthenticated and unsigned.
    private fun isSafeRedirectPath(path: String): Boolean =
        path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/\\") && !path.contains("://")
}
