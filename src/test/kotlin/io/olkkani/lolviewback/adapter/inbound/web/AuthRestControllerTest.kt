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
import io.olkkani.lolviewback.application.auth.AuthProvider
import io.olkkani.lolviewback.application.auth.IdentityAlreadyLinkedException
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.auth.RefreshTokenService
import io.olkkani.lolviewback.application.auth.ResolveIdentityService
import io.olkkani.lolviewback.application.auth.ResolveResult
import io.olkkani.lolviewback.application.auth.RotateResult
import io.olkkani.lolviewback.application.auth.TelegramLoginPayload
import io.olkkani.lolviewback.application.auth.TelegramLoginVerifier
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

class AuthRestControllerTest {
    private val userIdentityRepository = mockk<UserIdentityRepository>()
    private val userRepository = mockk<UserRepository>()
    private val jwtService = mockk<JwtService>()
    private val refreshTokenService = mockk<RefreshTokenService>()
    private val telegramLoginVerifier = mockk<TelegramLoginVerifier>()
    private val resolveIdentityService = mockk<ResolveIdentityService>()
    private val controller =
        AuthRestController(
            userIdentityRepository,
            userRepository,
            jwtService,
            refreshTokenService,
            telegramLoginVerifier,
            resolveIdentityService,
            accessExpirationMinutes = 30L,
            refreshExpirationDays = 14L,
        )

    private fun telegramPayload(id: Long = 111222333L): TelegramLoginPayload =
        TelegramLoginPayload(
            id = id,
            firstName = "Ada",
            lastName = null,
            username = "ada_lovelace",
            photoUrl = null,
            authDate = 1_700_000_000L,
            hash = "deadbeef",
        )

    @Test
    fun `refresh re-reads the current role from the database, not from the old token`() {
        every { userRepository.findById(7L) } returns Optional.of(User(id = 7L, role = Role.ADMIN))
        every { refreshTokenService.rotate("old-refresh-token") } returns
            RotateResult.Rotated(userId = 7L, newRawToken = "new-refresh-token")
        every { jwtService.issueToken(7L, Role.ADMIN) } returns "new-access-jwt"

        val request = mockk<HttpServletRequest>()
        every { request.cookies } returns arrayOf(Cookie("refresh_token", "old-refresh-token"))

        controller.refresh(request)

        verify { jwtService.issueToken(7L, Role.ADMIN) }
    }

    @Test
    fun `refresh returns 401 when there is no refresh_token cookie`() {
        val request = mockk<HttpServletRequest>()
        every { request.cookies } returns null

        val response = controller.refresh(request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `GET auth me returns the identity list for the authenticated user`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("88", null, emptyList<SimpleGrantedAuthority>())
        every { userIdentityRepository.findByUserId(88L) } returns
            listOf(
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

        val response = controller.refresh(request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        val setCookies = response.headers[HttpHeaders.SET_COOKIE].orEmpty()
        assertTrue(setCookies.contains(CookieSupport.expiredAccessTokenCookie().toString()))
        assertTrue(setCookies.contains(CookieSupport.expiredRefreshTokenCookie().toString()))
    }

    @Test
    fun `POST auth logout revokes the refresh token and clears cookies`() {
        every { refreshTokenService.revoke("some-refresh-value") } returns Unit

        val request = mockk<HttpServletRequest>()
        every { request.cookies } returns arrayOf(Cookie("refresh_token", "some-refresh-value"))

        val response = controller.logout(request)

        verify { refreshTokenService.revoke("some-refresh-value") }
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        val setCookies = response.headers[HttpHeaders.SET_COOKIE].orEmpty()
        assertTrue(setCookies.contains(CookieSupport.expiredAccessTokenCookie().toString()))
        assertTrue(setCookies.contains(CookieSupport.expiredRefreshTokenCookie().toString()))
    }

    @Test
    fun `telegram callback with an invalid hash returns 401`() {
        val payload = telegramPayload()
        every { telegramLoginVerifier.isValid(payload) } returns false

        val response = controller.telegramCallback(payload)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        verify(exactly = 0) { resolveIdentityService.resolveIdentity(any(), any(), any()) }
    }

    @Test
    fun `telegram callback for a new user sets access and refresh cookies`() {
        val payload = telegramPayload(id = 111222333L)
        every { telegramLoginVerifier.isValid(payload) } returns true
        every {
            resolveIdentityService.resolveIdentity(AuthProvider.TELEGRAM, "111222333", null)
        } returns ResolveResult.NewUser(userId = 5L)
        every { userRepository.findById(5L) } returns Optional.of(User(id = 5L, role = Role.USER))
        every { jwtService.issueToken(5L, Role.USER) } returns "issued-access-jwt"
        every { refreshTokenService.issue(5L) } returns "issued-refresh-token"

        val response = controller.telegramCallback(payload)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        val setCookies = response.headers[HttpHeaders.SET_COOKIE].orEmpty()
        assertTrue(setCookies.any { it.startsWith("access_token=issued-access-jwt") })
        assertTrue(setCookies.any { it.startsWith("refresh_token=issued-refresh-token") })
    }

    @Test
    fun `telegram callback for an identity already linked elsewhere returns 409`() {
        val payload = telegramPayload()
        every { telegramLoginVerifier.isValid(payload) } returns true
        every {
            resolveIdentityService.resolveIdentity(AuthProvider.TELEGRAM, "111222333", null)
        } returns ResolveResult.AlreadyLinkedElsewhere

        val response = controller.telegramCallback(payload)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `telegram callback propagates a concurrent identity-link race as 409`() {
        val payload = telegramPayload()
        every { telegramLoginVerifier.isValid(payload) } returns true
        every {
            resolveIdentityService.resolveIdentity(AuthProvider.TELEGRAM, "111222333", null)
        } throws IdentityAlreadyLinkedException()

        val response = controller.telegramCallback(payload)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }
}
