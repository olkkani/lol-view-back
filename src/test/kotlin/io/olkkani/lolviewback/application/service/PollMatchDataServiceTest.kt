package io.olkkani.lolviewback.application.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchApiResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.TournamentPollingDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.application.outbound.LolApiClientPort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class PollMatchDataServiceTest {

    private fun league(apiId: String = "lck-api-id") = League(
        leagueName = "LCK",
        logoUrl = "https://example.com/lck.png",
        isActive = true,
        leagueApiId = apiId,
    )

    private fun tournament(league: League, apiId: String = "t-1") = Tournament(
        tournamentName = "LCK 2026 Spring",
        startDate = LocalDate.now().minusDays(3),
        endDate = LocalDate.now().plusDays(30),
        tournamentApiId = apiId,
        league = league,
    )

    private fun matchApiResponse(apiId: String) = MatchApiResponse(
        apiId = apiId,
        startTime = ZonedDateTime.now(),
        strategyType = "BO3",
        state = "unstarted",
        label = "Week 1",
    )

    @Test
    fun `fetches and saves matches not already persisted for a due tournament`() = runBlocking {
        val tournamentRepository = mockk<TournamentRepository>()
        val tournamentPollingDao = mockk<TournamentPollingDao>()
        val matchRepository = mockk<MatchRepository>()
        val apiClientPort = mockk<LolApiClientPort>()

        val league = league()
        val dueTournament = tournament(league)
        val newMatch = matchApiResponse("match-new")
        val alreadySavedMatch = matchApiResponse("match-existing")

        every { tournamentRepository.findAll() } returns listOf(dueTournament)
        every { tournamentPollingDao.findInProgressTournaments() } returns emptyList()
        coEvery { apiClientPort.fetchMatchesForLeague(league.leagueApiId) } returns listOf(newMatch, alreadySavedMatch)
        every { matchRepository.findByMatchApiId("match-new") } returns null
        every { matchRepository.findByMatchApiId("match-existing") } returns mockk<Match>()
        every { matchRepository.save(any()) } answers { firstArg() }

        val service = PollMatchDataService(tournamentRepository, tournamentPollingDao, matchRepository, apiClientPort)

        service.syncUpcomingMatches()

        verify(exactly = 1) { matchRepository.save(match { it.matchApiId == "match-new" }) }
        verify(exactly = 0) { matchRepository.save(match { it.matchApiId == "match-existing" }) }
    }

    @Test
    fun `skips leagues with no due tournament without calling the api client`() = runBlocking {
        val tournamentRepository = mockk<TournamentRepository>()
        val tournamentPollingDao = mockk<TournamentPollingDao>()
        val matchRepository = mockk<MatchRepository>()
        val apiClientPort = mockk<LolApiClientPort>()

        val league = league()
        val notDueTournament = Tournament(
            tournamentName = "LCK 2025 Summer",
            startDate = LocalDate.now().minusDays(60),
            endDate = LocalDate.now().minusDays(30),
            tournamentApiId = "t-old",
            league = league,
        )

        every { tournamentRepository.findAll() } returns listOf(notDueTournament)
        every { tournamentPollingDao.findInProgressTournaments() } returns emptyList()

        val service = PollMatchDataService(tournamentRepository, tournamentPollingDao, matchRepository, apiClientPort)

        service.syncUpcomingMatches()

        verify(exactly = 0) { matchRepository.save(any()) }
    }

    @Test
    fun `a failure fetching one league does not prevent syncing the next league`() = runBlocking {
        val tournamentRepository = mockk<TournamentRepository>()
        val tournamentPollingDao = mockk<TournamentPollingDao>()
        val matchRepository = mockk<MatchRepository>()
        val apiClientPort = mockk<LolApiClientPort>()

        val failingLeague = league(apiId = "failing-league")
        val okLeague = league(apiId = "ok-league")
        val failingTournament = tournament(failingLeague, apiId = "t-fail")
        val okTournament = tournament(okLeague, apiId = "t-ok")
        val okMatch = matchApiResponse("match-ok")

        every { tournamentRepository.findAll() } returns listOf(failingTournament, okTournament)
        every { tournamentPollingDao.findInProgressTournaments() } returns emptyList()
        coEvery { apiClientPort.fetchMatchesForLeague("failing-league") } throws RuntimeException("boom")
        coEvery { apiClientPort.fetchMatchesForLeague("ok-league") } returns listOf(okMatch)
        every { matchRepository.findByMatchApiId("match-ok") } returns null
        every { matchRepository.save(any()) } answers { firstArg() }

        val service = PollMatchDataService(tournamentRepository, tournamentPollingDao, matchRepository, apiClientPort)

        service.syncUpcomingMatches()

        verify(exactly = 1) { matchRepository.save(match { it.matchApiId == "match-ok" }) }
    }
}
