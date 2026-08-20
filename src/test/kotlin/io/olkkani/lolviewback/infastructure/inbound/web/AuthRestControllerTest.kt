package io.olkkani.lolviewback.infastructure.inbound.web

import io.mockk.every
import io.mockk.mockk
import io.olkkani.lolviewback.domain.auth.JwtService
import io.olkkani.lolviewback.infastructure.outbound.repository.UserIdentityRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.UserIdentity
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(AuthRestController::class, excludeAutoConfiguration = [OAuth2ClientWebSecurityAutoConfiguration::class])
@Import(AuthRestControllerTest.MockConfig::class)
class AuthRestControllerTest {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun userIdentityRepository(): UserIdentityRepository = mockk()

        // @WebMvcTest slices pull in JwtAuthenticationFilter (a @Component in the
        // web-security package tree) regardless of whether SecurityConfig is part of
        // the slice, so JwtService must be stubbed here too. Pre-existing pattern gap
        // documented in Task 9's report and repeated in ClubRestControllerTest (Task 10);
        // applied here for the same reason.
        @Bean
        fun jwtService(): JwtService = mockk()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userIdentityRepository: UserIdentityRepository

    @Test
    fun `GET me returns the identity list for the authenticated user`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("88", null, emptyList<SimpleGrantedAuthority>())
        every { userIdentityRepository.findByUserId(88L) } returns listOf(
            UserIdentity(id = 1L, userId = 88L, provider = "GOOGLE", providerUserId = "sub-88"),
        )

        mockMvc.get("/me")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].provider") { value("GOOGLE") }
                jsonPath("$[0].providerUserId") { value("sub-88") }
            }

        SecurityContextHolder.clearContext()
    }
}
