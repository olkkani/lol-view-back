package io.olkkani.lolviewback.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.persistence.UserIdentityRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.UserIdentity
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.auth.RefreshTokenService
import io.olkkani.lolviewback.application.auth.RotateResult
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(AuthRestController::class, excludeAutoConfiguration = [OAuth2ClientWebSecurityAutoConfiguration::class])
@Import(AuthRestControllerTest.MockConfig::class)
@TestPropertySource(
    properties = [
        "jwt.access-expiration-minutes=30",
        "jwt.refresh-expiration-days=14",
        "jwt.refresh-grace-period-seconds=20",
        "jwt.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=",
    ],
)
class AuthRestControllerTest {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun userIdentityRepository(): UserIdentityRepository = mockk()

        @Bean
        fun jwtService(): JwtService = mockk()

        @Bean
        fun refreshTokenService(): RefreshTokenService = mockk()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userIdentityRepository: UserIdentityRepository

    @Autowired
    lateinit var jwtService: JwtService

    @Autowired
    lateinit var refreshTokenService: RefreshTokenService

    @Test
    fun `GET auth me returns the identity list for the authenticated user`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("88", null, emptyList<SimpleGrantedAuthority>())
        every { userIdentityRepository.findByUserId(88L) } returns listOf(
            UserIdentity(id = 1L, userId = 88L, provider = "GOOGLE", providerUserId = "sub-88"),
        )

        mockMvc.get("/auth/me")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].provider") { value("GOOGLE") }
                jsonPath("$[0].providerUserId") { value("sub-88") }
            }

        SecurityContextHolder.clearContext()
    }

    @Test
    fun `POST auth refresh with a valid refresh cookie returns 200 and sets new cookies`() {
        every { refreshTokenService.rotate("valid-refresh-value") } returns RotateResult.Rotated(userId = 88L, newRawToken = "new-refresh-value")
        every { jwtService.issueToken(88L) } returns "new-access-value"

        mockMvc.post("/auth/refresh") {
            cookie(Cookie("refresh_token", "valid-refresh-value"))
        }.andExpect {
            status { isOk() }
            header { exists("Set-Cookie") }
        }
    }

    @Test
    fun `POST auth refresh with a stolen refresh cookie returns 401`() {
        every { refreshTokenService.rotate("stolen-refresh-value") } returns RotateResult.TheftDetected

        mockMvc.post("/auth/refresh") {
            cookie(Cookie("refresh_token", "stolen-refresh-value"))
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `POST auth refresh with no refresh cookie returns 401`() {
        mockMvc.post("/auth/refresh")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `POST auth logout revokes the refresh token and clears cookies`() {
        every { refreshTokenService.revoke("some-refresh-value") } returns Unit

        mockMvc.post("/auth/logout") {
            cookie(Cookie("refresh_token", "some-refresh-value"))
        }.andExpect {
            status { isOk() }
            header { exists("Set-Cookie") }
        }
        verify { refreshTokenService.revoke("some-refresh-value") }
    }
}
