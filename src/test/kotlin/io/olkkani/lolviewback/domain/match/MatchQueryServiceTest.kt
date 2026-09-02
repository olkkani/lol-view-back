package io.olkkani.lolviewback.domain.match

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchParticipantRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchSetDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Club
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubProfile
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchParticipant
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.adapter.outbound.persistence.projection.MatchSetProjection
import io.olkkani.lolviewback.application.service.MatchQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MatchQueryServiceTest {

    private val matchRepository = mockk<MatchRepository>()
    private val matchParticipantRepository = mockk<MatchParticipantRepository>()
    private val matchSetDao = mockk<MatchSetDao>()
    private val service = MatchQueryService(matchRepository, matchParticipantRepository, matchSetDao)
    private val kst = ZoneId.of("Asia/Seoul")

    @Test
    fun `findMatches queries repository with today's date range and sorts by startTime ascending`() {
        val today = LocalDate.of(2026, 8, 12)
        val league = mockk<League>()
        every { league.leagueName } returns "LCK"
        val tournament = mockk<Tournament>()
        every { tournament.league } returns league
        val laterMatch = Match(
            id = 2L,
            startTime = ZonedDateTime.of(2026, 8, 12, 20, 0, 0, 0, kst),
            matchType = MatchType.BO3,
            matchState = MatchState.UNSTARTED,
            matchLabel = "W1",
            matchApiId = "m2",
            tournament = tournament,
        )
        val earlierMatch = Match(
            id = 1L,
            startTime = ZonedDateTime.of(2026, 8, 12, 14, 0, 0, 0, kst),
            matchType = MatchType.BO3,
            matchState = MatchState.UNSTARTED,
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
        every { matchSetDao.findWinCountsByMatchIdIn(listOf(1L, 2L), any()) } returns emptyList<MatchSetProjection>()

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
        every { matchSetDao.findWinCountsByMatchIdIn(emptyList(), emptyMap()) } returns emptyList<MatchSetProjection>()

        val result = service.findMatches(MatchRange.TODAY, today)

        assertEquals(emptyList<Long>(), result.map { it.id })
        verify(exactly = 1) { matchParticipantRepository.findByMatchIdIn(emptyList()) }
    }

    @Test
    fun `findMatches sources each club's score from MatchSet win counts, not MatchParticipant`() {
        val today = LocalDate.of(2026, 8, 12)
        val league = mockk<League>()
        every { league.leagueName } returns "LCK"
        val tournament = mockk<Tournament>()
        every { tournament.league } returns league
        val match = Match(
            id = 1L,
            startTime = ZonedDateTime.of(2026, 8, 12, 14, 0, 0, 0, kst),
            matchType = MatchType.BO3,
            matchState = MatchState.COMPLETED,
            matchLabel = "W1",
            matchApiId = "m1",
            tournament = tournament,
        )
        val expectedStart = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst)
        val expectedEnd = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst)

        val t1ClubProfile = ClubProfile(
            id = 100L,
            clubName = "T1",
            abbreviation = "T1",
            logoUrl = "https://example.com/t1.png",
            effectiveFrom = LocalDate.of(2020, 1, 1),
            effectiveTo = LocalDate.of(2099, 1, 1),
        )
        val genClubProfile = ClubProfile(
            id = 200L,
            clubName = "Gen.G",
            abbreviation = "GEN",
            logoUrl = "https://example.com/gen.png",
            effectiveFrom = LocalDate.of(2020, 1, 1),
            effectiveTo = LocalDate.of(2099, 1, 1),
        )
        val participants = listOf(
            MatchParticipant(id = 1L, isWin = null, score = 0, match = match, club = Club(id = 10L, isActive = true), clubProfile = t1ClubProfile),
            MatchParticipant(id = 2L, isWin = null, score = 0, match = match, club = Club(id = 20L, isActive = true), clubProfile = genClubProfile),
        )

        every {
            matchRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(expectedStart, expectedEnd)
        } returns listOf(match)
        every { matchParticipantRepository.findByMatchIdIn(listOf(1L)) } returns participants
        every {
            matchSetDao.findWinCountsByMatchIdIn(listOf(1L), mapOf(1L to LocalDate.of(2026, 8, 12)))
        } returns listOf(
            MatchSetProjection(matchId = 1L, clubId = 10L, abbreviation = "T1", wins = 2),
            MatchSetProjection(matchId = 1L, clubId = 20L, abbreviation = "GEN", wins = 1),
        )

        val result = service.findMatches(MatchRange.TODAY, today)

        val scores = result.single().clubs.associate { it.name to it.score }
        assertEquals(mapOf("T1" to 2, "GEN" to 1), scores)
    }
}
