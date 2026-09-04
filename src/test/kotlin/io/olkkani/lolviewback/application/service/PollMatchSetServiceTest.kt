package io.olkkani.lolviewback.application.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.persistence.ClubRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchSetDao
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchSetRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.application.outbound.LolApiClientPort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class PollMatchSetServiceTest {

    private fun match(apiId: String = "match-1") = Match(
        startTime = ZonedDateTime.now(),
        matchType = MatchType.BO3,
        matchState = MatchState.IN_PROGRESS,
        matchLabel = "Week 1",
        matchApiId = apiId,
        tournament = Tournament(
            tournamentName = "LCK 2026 Spring",
            startDate = LocalDate.now().minusDays(3),
            endDate = LocalDate.now().plusDays(30),
            tournamentApiId = "t-1",
            league = League(
                leagueName = "LCK",
                logoUrl = "https://example.com/lck.png",
                isActive = true,
                leagueApiId = "lck-api-id",
            ),
        ),
    )

    @Test
    fun `skips syncing a match when the api reports no event data yet`() = runBlocking {
        val apiClientPort = mockk<LolApiClientPort>()
        val matchRepository = mockk<MatchRepository>()
        val matchSetRepository = mockk<MatchSetRepository>()
        val clubRepository = mockk<ClubRepository>()
        val matchSetDao = mockk<MatchSetDao>()

        val inProgressMatch = match()

        every { matchRepository.findAllByMatchState(MatchState.IN_PROGRESS) } returns listOf(inProgressMatch)
        every { matchSetDao.findWinCountsByMatchIdIn(any(), any()) } returns emptyList()
        coEvery { apiClientPort.fetchMatchSet(inProgressMatch.matchApiId) } returns null

        val service = PollMatchSetService(apiClientPort, matchRepository, matchSetRepository, clubRepository, matchSetDao)

        service.syncMatchSets()

        verify(exactly = 0) { matchSetRepository.save(any()) }
        verify(exactly = 0) { matchRepository.save(any()) }
    }
}
