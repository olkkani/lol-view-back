package io.olkkani.lolviewback.infastructure.inbound.web

import io.mockk.every
import io.mockk.mockk
import io.olkkani.lolviewback.domain.match.MatchQueryService
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchClubResponse
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.ZoneId
import java.time.ZonedDateTime

@WebMvcTest(MatchRestController::class)
@Import(MatchRestControllerTest.MockConfig::class)
class MatchRestControllerTest {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun matchQueryService(): MatchQueryService = mockk()
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
            clubs = listOf(MatchClubResponse(name = "T1", logoUrl = "url", score = 1)),
        )
        every { matchQueryService.findMatches(MatchRange.TODAY) } returns listOf(response)

        mockMvc.get("/matches?range=today")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(1) }
                jsonPath("$[0].matchState") { value("ONGOING") }
            }
    }

    @Test
    fun `GET matches without range returns 400`() {
        mockMvc.get("/matches")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `GET matches with invalid range returns 400`() {
        mockMvc.get("/matches?range=tomorrow")
            .andExpect { status { isBadRequest() } }
    }
}
