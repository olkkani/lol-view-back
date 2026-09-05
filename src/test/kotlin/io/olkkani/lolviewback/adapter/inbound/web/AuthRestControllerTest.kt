package io.olkkani.lolviewback.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.inbound.security.CookieSupport
import io.olkkani.lolviewback.adapter.outbound.persistence.UserIdentityRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.UserRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Role
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.User
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.auth.RefreshTokenService
import io.olkkani.lolviewback.application.auth.RotateResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
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
}
