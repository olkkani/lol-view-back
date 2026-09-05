package io.olkkani.lolviewback.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.inbound.security.CookieSupport
import io.olkkani.lolviewback.adapter.outbound.persistence.UserIdentityRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.UserRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Role
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.User
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.UserIdentity
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.auth.RefreshTokenService
import io.olkkani.lolviewback.application.auth.RotateResult
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

class AuthRestControllerTest {

    private val userIdentityRepository = mockk<UserIdentityRepository>()
    private val userRepository = mockk<UserRepository>()
    private val jwtService = mockk<JwtService>()
    private val refreshTokenService = mockk<RefreshTokenService>()
    private val controller = AuthRestController(
        userIdentityRepository,
        userRepository,
        jwtService,
        refreshTokenService,
        accessExpirationMinutes = 30L,
        refreshExpirationDays = 14L,
    )

    @Test
    fun `refresh re-reads the current role from the database, not from the old token`() {
        every { userRepository.findById(7L) } returns Optional.of(User(id = 7L, role = Role.ADMIN))
        every { refreshTokenService.rotate("old-refresh-token") } returns
            RotateResult.Rotated(userId = 7L, newRawToken = "new-refresh-token")
        every { jwtService.issueToken(7L, Role.ADMIN) } returns "new-access-jwt"

        val request = mockk<HttpServletRequest>()
        every { request.cookies } returns arrayOf(jakarta.servlet.http.Cookie("refresh_token", "old-refresh-token"))
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.refresh(request, response)

        verify { jwtService.issueToken(7L, Role.ADMIN) }
    }

    @Test
    fun `refresh returns 401 when there is no refresh_token cookie`() {
        val request = mockk<HttpServletRequest>()
        every { request.cookies } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.refresh(request, response)

        verify { response.status = HttpServletResponse.SC_UNAUTHORIZED }
    }

    @Test
    fun `GET auth me returns the identity list for the authenticated user`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("88", null, emptyList<SimpleGrantedAuthority>())
        every { userIdentityRepository.findByUserId(88L) } returns listOf(
            UserIdentity(id = 1L, userId = 88L, provider = "GOOGLE", providerUserId = "sub-88"),
        )

        val result = controller.getMe()

        assert(result.size == 1)
        assert(result[0].provider == "GOOGLE")
        assert(result[0].providerUserId == "sub-88")

        SecurityContextHolder.clearContext()
    }

    @Test
    fun `POST auth refresh with a stolen refresh cookie returns 401`() {
        every { refreshTokenService.rotate("stolen-refresh-value") } returns RotateResult.TheftDetected

        val request = mockk<HttpServletRequest>()
        every { request.cookies } returns arrayOf(Cookie("refresh_token", "stolen-refresh-value"))
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.refresh(request, response)

        verify { response.status = HttpServletResponse.SC_UNAUTHORIZED }
        verify { response.addHeader("Set-Cookie", CookieSupport.expiredAccessTokenCookie().toString()) }
        verify { response.addHeader("Set-Cookie", CookieSupport.expiredRefreshTokenCookie().toString()) }
    }

    @Test
    fun `POST auth logout revokes the refresh token and clears cookies`() {
        every { refreshTokenService.revoke("some-refresh-value") } returns Unit

        val request = mockk<HttpServletRequest>()
        every { request.cookies } returns arrayOf(Cookie("refresh_token", "some-refresh-value"))
        val response = mockk<HttpServletResponse>(relaxed = true)

        controller.logout(request, response)

        verify { refreshTokenService.revoke("some-refresh-value") }
        verify { response.addHeader("Set-Cookie", CookieSupport.expiredAccessTokenCookie().toString()) }
        verify { response.addHeader("Set-Cookie", CookieSupport.expiredRefreshTokenCookie().toString()) }
    }
}
