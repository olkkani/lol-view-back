package io.olkkani.lolviewback.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.match.ClubService
import io.olkkani.lolviewback.adapter.inbound.web.dto.ClubResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ClubRestController::class, excludeAutoConfiguration = [OAuth2ClientWebSecurityAutoConfiguration::class])
@Import(ClubRestControllerTest.MockConfig::class)
class ClubRestControllerTest {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun clubService(): ClubService = mockk()

        // @WebMvcTest slices pull in JwtAuthenticationFilter (a @Component in the
        // web-security package tree) regardless of whether SecurityConfig is part of
        // the slice, so JwtService must be stubbed here too. Pre-existing pattern gap
        // documented in Task 9's report (same root cause as MatchRestControllerTest's
        // failures); stubbed here rather than left broken since this task's own new
        // test needs the slice to boot.
        @Bean
        fun jwtService(): JwtService = mockk()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var clubService: ClubService

    @Test
    fun `GET clubs returns the authenticated user's clubs without a userId query param`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("77", null, emptyList<SimpleGrantedAuthority>())
        every { clubService.getClubs(77L) } returns listOf(
            ClubResponse(id = 1L, name = "T1", imageUrl = "url", region = "KR", isFollowed = true, clubId = "1000"),
        )

        mockMvc.get("/clubs")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].name") { value("T1") }
            }

        SecurityContextHolder.clearContext()
    }
}
