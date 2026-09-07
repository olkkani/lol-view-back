package io.olkkani.lolviewback.domain.match

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.inbound.web.dto.HeadToHeadResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.InvalidHeadToHeadRequestException
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchNotFoundException
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Optional

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
            MatchParticipant(id = 1L, match = match, club = Club(id = 10L, isActive = true), clubProfile = t1ClubProfile),
            MatchParticipant(id = 2L, match = match, club = Club(id = 20L, isActive = true), clubProfile = genClubProfile),
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

    private fun completedMatch(id: Long, startTime: ZonedDateTime, tournament: Tournament) = Match(
        id = id,
        startTime = startTime,
        matchType = MatchType.BO3,
        matchState = MatchState.COMPLETED,
        matchLabel = "W1",
        matchApiId = "m$id",
        tournament = tournament,
    )

    @Test
    fun `findHeadToHead resolves the two club ids from the requested match and queries head-to-head`() {
        val league = mockk<League>()
        every { league.leagueName } returns "LCK"
        val tournament = mockk<Tournament>()
        every { tournament.league } returns league

        val currentMatch = completedMatch(100L, ZonedDateTime.of(2026, 8, 12, 14, 0, 0, 0, kst), tournament)
        val clubA = Club(id = 10L, isActive = true)
        val clubB = Club(id = 20L, isActive = true)
        val clubProfileA = ClubProfile(clubName = "A", abbreviation = "A", logoUrl = "u", effectiveFrom = LocalDate.of(2020, 1, 1))
        val clubProfileB = ClubProfile(clubName = "B", abbreviation = "B", logoUrl = "u", effectiveFrom = LocalDate.of(2020, 1, 1))
        val currentParticipants = listOf(
            MatchParticipant(match = currentMatch, club = clubA, clubProfile = clubProfileA),
            MatchParticipant(match = currentMatch, club = clubB, clubProfile = clubProfileB),
        )

        val pastMatch = completedMatch(1L, ZonedDateTime.of(2026, 7, 1, 14, 0, 0, 0, kst), tournament)

        every { matchRepository.findById(100L) } returns Optional.of(currentMatch)
        every { matchParticipantRepository.findByMatchIdIn(listOf(100L)) } returns currentParticipants
        every {
            matchRepository.findHeadToHeadBefore(10L, 20L, currentMatch.startTime, PageRequest.of(0, 5))
        } returns listOf(pastMatch)
        every {
            matchSetDao.findWinCountsByMatchIdIn(listOf(1L), mapOf(1L to LocalDate.of(2026, 7, 1)))
        } returns listOf(MatchSetProjection(matchId = 1L, clubId = 10L, abbreviation = "A", wins = 2))

        val result = service.findHeadToHead(100L)

        assertEquals(listOf(HeadToHeadResponse(matchId = 1L, winnerClubId = 10L)), result)
    }

    @Test
    fun `findHeadToHead returns results ordered oldest-first even though the query fetches newest-first`() {
        val league = mockk<League>()
        every { league.leagueName } returns "LCK"
        val tournament = mockk<Tournament>()
        every { tournament.league } returns league

        val currentMatch = completedMatch(100L, ZonedDateTime.of(2026, 9, 1, 14, 0, 0, 0, kst), tournament)
        val clubA = Club(id = 10L, isActive = true)
        val clubB = Club(id = 20L, isActive = true)
        val clubProfileA = ClubProfile(clubName = "A", abbreviation = "A", logoUrl = "u", effectiveFrom = LocalDate.of(2020, 1, 1))
        val clubProfileB = ClubProfile(clubName = "B", abbreviation = "B", logoUrl = "u", effectiveFrom = LocalDate.of(2020, 1, 1))
        every { matchRepository.findById(100L) } returns Optional.of(currentMatch)
        every { matchParticipantRepository.findByMatchIdIn(listOf(100L)) } returns listOf(
            MatchParticipant(match = currentMatch, club = clubA, clubProfile = clubProfileA),
            MatchParticipant(match = currentMatch, club = clubB, clubProfile = clubProfileB),
        )

        // Repository returns DESC (newest first): match 3, then 2, then 1.
        val m3 = completedMatch(3L, ZonedDateTime.of(2026, 8, 3, 0, 0, 0, 0, kst), tournament)
        val m2 = completedMatch(2L, ZonedDateTime.of(2026, 8, 2, 0, 0, 0, 0, kst), tournament)
        val m1 = completedMatch(1L, ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, kst), tournament)
        every {
            matchRepository.findHeadToHeadBefore(10L, 20L, currentMatch.startTime, PageRequest.of(0, 5))
        } returns listOf(m3, m2, m1)
        every {
            matchSetDao.findWinCountsByMatchIdIn(listOf(3L, 2L, 1L), any())
        } returns listOf(
            MatchSetProjection(matchId = 3L, clubId = 10L, abbreviation = "A", wins = 2),
            MatchSetProjection(matchId = 2L, clubId = 20L, abbreviation = "B", wins = 2),
            MatchSetProjection(matchId = 1L, clubId = 10L, abbreviation = "A", wins = 2),
        )

        val result = service.findHeadToHead(100L)

        // Oldest first: match 1, then 2, then 3.
        assertEquals(listOf(1L, 2L, 3L), result.map { it.matchId })
    }

    @Test
    fun `findHeadToHead excludes a COMPLETED match with no synced MatchSet rows instead of throwing`() {
        val league = mockk<League>()
        every { league.leagueName } returns "LCK"
        val tournament = mockk<Tournament>()
        every { tournament.league } returns league

        val currentMatch = completedMatch(100L, ZonedDateTime.of(2026, 9, 1, 14, 0, 0, 0, kst), tournament)
        val clubA = Club(id = 10L, isActive = true)
        val clubB = Club(id = 20L, isActive = true)
        val clubProfileA = ClubProfile(clubName = "A", abbreviation = "A", logoUrl = "u", effectiveFrom = LocalDate.of(2020, 1, 1))
        val clubProfileB = ClubProfile(clubName = "B", abbreviation = "B", logoUrl = "u", effectiveFrom = LocalDate.of(2020, 1, 1))
        every { matchRepository.findById(100L) } returns Optional.of(currentMatch)
        every { matchParticipantRepository.findByMatchIdIn(listOf(100L)) } returns listOf(
            MatchParticipant(match = currentMatch, club = clubA, clubProfile = clubProfileA),
            MatchParticipant(match = currentMatch, club = clubB, clubProfile = clubProfileB),
        )

        val syncedMatch = completedMatch(1L, ZonedDateTime.of(2026, 8, 2, 0, 0, 0, 0, kst), tournament)
        val laggingMatch = completedMatch(2L, ZonedDateTime.of(2026, 8, 3, 0, 0, 0, 0, kst), tournament)
        every {
            matchRepository.findHeadToHeadBefore(10L, 20L, currentMatch.startTime, PageRequest.of(0, 5))
        } returns listOf(laggingMatch, syncedMatch)
        every {
            matchSetDao.findWinCountsByMatchIdIn(listOf(2L, 1L), any())
        } returns listOf(
            // No projection for matchId=2L: sync hasn't produced MatchSet rows yet.
            MatchSetProjection(matchId = 1L, clubId = 10L, abbreviation = "A", wins = 2),
        )

        val result = service.findHeadToHead(100L)

        assertEquals(listOf(1L), result.map { it.matchId })
    }

    @Test
    fun `findHeadToHead throws MatchNotFoundException when matchId does not exist`() {
        every { matchRepository.findById(999L) } returns Optional.empty()

        assertThrows(MatchNotFoundException::class.java) { service.findHeadToHead(999L) }
    }

    @Test
    fun `findHeadToHead throws InvalidHeadToHeadRequestException when fewer than 2 club participants exist`() {
        val league = mockk<League>()
        every { league.leagueName } returns "LCK"
        val tournament = mockk<Tournament>()
        every { tournament.league } returns league
        val currentMatch = completedMatch(100L, ZonedDateTime.of(2026, 8, 12, 14, 0, 0, 0, kst), tournament)
        val clubA = Club(id = 10L, isActive = true)
        val clubProfileA = ClubProfile(clubName = "A", abbreviation = "A", logoUrl = "u", effectiveFrom = LocalDate.of(2020, 1, 1))

        every { matchRepository.findById(100L) } returns Optional.of(currentMatch)
        every { matchParticipantRepository.findByMatchIdIn(listOf(100L)) } returns listOf(
            MatchParticipant(match = currentMatch, club = clubA, clubProfile = clubProfileA),
        )

        assertThrows(InvalidHeadToHeadRequestException::class.java) { service.findHeadToHead(100L) }
    }

    @Test
    fun `findHeadToHead throws InvalidHeadToHeadRequestException when both participants share the same club id`() {
        val league = mockk<League>()
        every { league.leagueName } returns "LCK"
        val tournament = mockk<Tournament>()
        every { tournament.league } returns league
        val currentMatch = completedMatch(100L, ZonedDateTime.of(2026, 8, 12, 14, 0, 0, 0, kst), tournament)
        val clubA = Club(id = 10L, isActive = true)
        val clubProfileA = ClubProfile(clubName = "A", abbreviation = "A", logoUrl = "u", effectiveFrom = LocalDate.of(2020, 1, 1))
        val clubProfileA2 = ClubProfile(clubName = "A2", abbreviation = "A2", logoUrl = "u", effectiveFrom = LocalDate.of(2020, 1, 1))

        every { matchRepository.findById(100L) } returns Optional.of(currentMatch)
        every { matchParticipantRepository.findByMatchIdIn(listOf(100L)) } returns listOf(
            MatchParticipant(match = currentMatch, club = clubA, clubProfile = clubProfileA),
            MatchParticipant(match = currentMatch, club = clubA, clubProfile = clubProfileA2),
        )

        assertThrows(InvalidHeadToHeadRequestException::class.java) { service.findHeadToHead(100L) }
    }

    @Test
    fun `findHeadToHead returns an empty list when no head-to-head matches exist`() {
        val league = mockk<League>()
        every { league.leagueName } returns "LCK"
        val tournament = mockk<Tournament>()
        every { tournament.league } returns league
        val currentMatch = completedMatch(100L, ZonedDateTime.of(2026, 8, 12, 14, 0, 0, 0, kst), tournament)
        val clubA = Club(id = 10L, isActive = true)
        val clubB = Club(id = 20L, isActive = true)
        val clubProfileA = ClubProfile(clubName = "A", abbreviation = "A", logoUrl = "u", effectiveFrom = LocalDate.of(2020, 1, 1))
        val clubProfileB = ClubProfile(clubName = "B", abbreviation = "B", logoUrl = "u", effectiveFrom = LocalDate.of(2020, 1, 1))

        every { matchRepository.findById(100L) } returns Optional.of(currentMatch)
        every { matchParticipantRepository.findByMatchIdIn(listOf(100L)) } returns listOf(
            MatchParticipant(match = currentMatch, club = clubA, clubProfile = clubProfileA),
            MatchParticipant(match = currentMatch, club = clubB, clubProfile = clubProfileB),
        )
        every {
            matchRepository.findHeadToHeadBefore(10L, 20L, currentMatch.startTime, PageRequest.of(0, 5))
        } returns emptyList()

        val result = service.findHeadToHead(100L)

        assertEquals(emptyList<HeadToHeadResponse>(), result)
    }
}
