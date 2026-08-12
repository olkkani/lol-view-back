package io.olkkani.lolviewback.domain.match

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.infastructure.outbound.repository.MatchParticipantRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.MatchRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchParticipant
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchType
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MatchQueryServiceTest {

    private val matchRepository = mockk<MatchRepository>()
    private val matchParticipantRepository = mockk<MatchParticipantRepository>()
    private val service = MatchQueryService(matchRepository, matchParticipantRepository)
    private val kst = ZoneId.of("Asia/Seoul")

    @Test
    fun `findMatches queries repository with today's date range and sorts by startTime ascending`() {
        val today = LocalDate.of(2026, 8, 12)
        val tournament = mockk<Tournament>()
        val laterMatch = Match(
            id = 2L,
            startTime = ZonedDateTime.of(2026, 8, 12, 20, 0, 0, 0, kst),
            matchType = MatchType.BO3,
            matchState = MatchState.SCHEDULED,
            matchLabel = "W1",
            matchApiId = "m2",
            tournament = tournament,
        )
        val earlierMatch = Match(
            id = 1L,
            startTime = ZonedDateTime.of(2026, 8, 12, 14, 0, 0, 0, kst),
            matchType = MatchType.BO3,
            matchState = MatchState.SCHEDULED,
            matchLabel = "W1",
            matchApiId = "m1",
            tournament = tournament,
        )

        val expectedStart = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst)
        val expectedEnd = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst)

        every {
            matchRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(expectedStart, expectedEnd)
        } returns listOf(laterMatch, earlierMatch)
        every { matchParticipantRepository.findByMatchIdIn(listOf(1L, 2L)) } returns emptyList<MatchParticipant>()

        val result = service.findMatches(MatchRange.TODAY, today)

        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun `findMatches returns an empty list when no matches fall in the range`() {
        val today = LocalDate.of(2026, 8, 12)
        val expectedStart = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst)
        val expectedEnd = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst)

        every {
            matchRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(expectedStart, expectedEnd)
        } returns emptyList()
        every { matchParticipantRepository.findByMatchIdIn(emptyList()) } returns emptyList<MatchParticipant>()

        val result = service.findMatches(MatchRange.TODAY, today)

        assertEquals(emptyList<Long>(), result.map { it.id })
        verify(exactly = 1) { matchParticipantRepository.findByMatchIdIn(emptyList()) }
    }
}
