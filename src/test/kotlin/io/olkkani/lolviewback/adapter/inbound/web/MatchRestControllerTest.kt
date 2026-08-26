package io.olkkani.lolviewback.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.match.MatchQueryService
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchClubResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LogoBackdrop
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import jakarta.servlet.ServletException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.ZoneId
import java.time.ZonedDateTime

@WebMvcTest(MatchRestController::class, excludeAutoConfiguration = [OAuth2ClientWebSecurityAutoConfiguration::class])
@Import(MatchRestControllerTest.MockConfig::class)
class MatchRestControllerTest {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun matchQueryService(): MatchQueryService = mockk()

        @Bean
        fun jwtService(): JwtService = mockk()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var matchQueryService: MatchQueryService

    private val kst = ZoneId.of("Asia/Seoul")

    @Test
    fun `GET matches with range=today returns 200 with match list`() {
        val response = MatchResponse(
            id = 1L,
            startTime = ZonedDateTime.of(2026, 8, 12, 18, 0, 0, 0, kst),
            matchState = MatchState.ONGOING,
            matchLabel = "W1",
            leagueName = "LCK",
            clubs = listOf(MatchClubResponse(name = "T1", logoUrl = "url", logoBackdrop = LogoBackdrop.DARK, score = 1)),
        )
        every { matchQueryService.findMatches(MatchRange.TODAY) } returns listOf(response)

        mockMvc.get("/matches?range=today")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(1) }
                jsonPath("$[0].matchState") { value("ONGOING") }
                jsonPath("$[0].clubs[0].logoBackdrop") { value("DARK") }
            }
    }

    @Test
    fun `GET matches serializes an unset logoBackdrop as JSON null`() {
        val response = MatchResponse(
            id = 1L,
            startTime = ZonedDateTime.of(2026, 8, 12, 18, 0, 0, 0, kst),
            matchState = MatchState.ONGOING,
            matchLabel = "W1",
            leagueName = "LCK",
            clubs = listOf(MatchClubResponse(name = "T1", logoUrl = "url", logoBackdrop = null, score = 1)),
        )
        every { matchQueryService.findMatches(MatchRange.TODAY) } returns listOf(response)

        mockMvc.get("/matches?range=today")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].clubs[0].logoBackdrop") { value(org.hamcrest.Matchers.nullValue()) }
            }
    }

    @Test
    fun `GET matches without range returns 400`() {
        mockMvc.get("/matches")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `GET matches with invalid range returns 400 with a JSON error body`() {
        mockMvc.get("/matches?range=tomorrow")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("Unknown range: tomorrow") }
            }
    }

    @Test
    fun `an IllegalArgumentException from the service is not mislabelled as a 400`() {
        val failure = IllegalArgumentException("internal failure")
        every { matchQueryService.findMatches(MatchRange.TODAY) } throws failure

        // The handler only catches InvalidMatchRangeException, so a genuine internal
        // error propagates out of the dispatcher instead of being reported as a
        // client-side 400. MockMvc surfaces that as the unhandled cause.
        val thrown = assertThrows(ServletException::class.java) {
            mockMvc.get("/matches?range=today")
        }
        assertEquals("internal failure", thrown.cause?.message)
    }
}
