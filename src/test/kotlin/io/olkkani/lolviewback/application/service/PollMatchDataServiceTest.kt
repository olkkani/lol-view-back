package io.olkkani.lolviewback.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEvent
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEventMatch
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEventStrategy
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEventTeam
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.ClubProfileRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchPollingDao
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.TournamentPollingDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Club
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubProfile
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchParticipant
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.application.outbound.LolApiClientPort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class PollMatchDataServiceTest {

    private val tournamentRepository = mockk<TournamentRepository>()
    private val tournamentPollingDao = mockk<TournamentPollingDao>()
    private val matchPollingDao = mockk<MatchPollingDao>()
    private val clubProfileRepository = mockk<ClubProfileRepository>()
    private val apiClientPort = mockk<LolApiClientPort>()

    private val service = PollMatchDataService(
        tournamentRepository,
        tournamentPollingDao,
        matchPollingDao,
        clubProfileRepository,
        apiClientPort,
    )

    private fun league(apiId: String = "lck-api-id") = League(
        leagueName = "LCK",
        logoUrl = "https://example.com/lck.png",
        isActive = true,
        leagueApiId = apiId,
    )

    private fun tournament(league: League, apiId: String = "t-1") = Tournament(
        id = 1L,
        tournamentName = "LCK 2026 Spring",
        startDate = LocalDate.now().minusDays(3),
        endDate = LocalDate.now().plusDays(30),
        tournamentApiId = apiId,
        league = league,
    )

    private fun team(code: String, name: String = code) = MatchScheduleEventTeam(
        name = name,
        code = code,
        image = "https://example.com/$code.png",
    )

    private fun scheduleEvent(
        matchApiId: String,
        state: String = "unstarted",
        teams: List<MatchScheduleEventTeam> = listOf(team("T1"), team("GEN")),
        boCount: Int = 3,
    ) = MatchScheduleEvent(
        startTime = ZonedDateTime.now(),
        state = state,
        blockName = "Week 1",
        match = MatchScheduleEventMatch(
            id = matchApiId,
            teams = teams,
            strategy = MatchScheduleEventStrategy(type = "bestOf", count = boCount),
        ),
    )

    private fun clubProfile(abbreviation: String, club: Club? = Club(isActive = true)) = ClubProfile(
        clubName = abbreviation,
        abbreviation = abbreviation,
        logoUrl = "https://example.com/$abbreviation.png",
        effectiveFrom = LocalDate.now().minusYears(1),
        club = club,
    )

    /** [MatchPollingDao.upsertMatches] returns each argument with a stand-in id assigned and matchApiId preserved, mirroring the DB round-trip. */
    private fun stubUpsertMatches() {
        every { matchPollingDao.upsertMatches(any<List<Match>>()) } answers {
            firstArg<List<Match>>().mapIndexed { index, match -> match.also { it.id = index + 1L } }
        }
    }

    @Test
    fun `saves an unstarted match for an in-progress tournament`() = runBlocking {
        val league = league()
        val tournament = tournament(league)
        val event = scheduleEvent("match-new")

        every { tournamentPollingDao.findInProgressTournaments() } returns listOf(tournament)
        every { tournamentRepository.getReferenceById(tournament.id) } returns tournament
        coEvery { apiClientPort.fetchMatches(league.leagueApiId) } returns listOf(event)
        stubUpsertMatches()
        every { clubProfileRepository.findByAbbreviationIn(any()) } returns listOf(clubProfile("T1"), clubProfile("GEN"))
        every { matchPollingDao.upsertParticipants(any<List<MatchParticipant>>()) } returns Unit

        service.syncUpcomingMatches()

        verify(exactly = 1) {
            matchPollingDao.upsertMatches(
                match<List<Match>> { matches ->
                    matches.size == 1 &&
                        matches[0].matchApiId == "match-new" &&
                        matches[0].matchType == MatchType.BO3 &&
                        matches[0].matchLabel == "Week 1"
                },
            )
        }
    }

    @Test
    fun `does not save events that are not unstarted`() = runBlocking {
        val league = league()
        val tournament = tournament(league)
        val completedEvent = scheduleEvent("match-completed", state = "completed")
        val inProgressEvent = scheduleEvent("match-in-progress", state = "inProgress")

        every { tournamentPollingDao.findInProgressTournaments() } returns listOf(tournament)
        coEvery { apiClientPort.fetchMatches(league.leagueApiId) } returns listOf(completedEvent, inProgressEvent)

        service.syncUpcomingMatches()

        verify(exactly = 0) { matchPollingDao.upsertMatches(any<List<Match>>()) }
        verify(exactly = 0) { tournamentRepository.getReferenceById(any()) }
    }

    @Test
    fun `does not call the api client when no tournament is in progress`() = runBlocking {
        every { tournamentPollingDao.findInProgressTournaments() } returns emptyList()

        service.syncUpcomingMatches()

        coVerify(exactly = 0) { apiClientPort.fetchMatches(any()) }
        verify(exactly = 0) { matchPollingDao.upsertMatches(any<List<Match>>()) }
    }

    @Test
    fun `reuses existing club profiles instead of creating new ones`() = runBlocking {
        val league = league()
        val tournament = tournament(league)
        val event = scheduleEvent("match-new", teams = listOf(team("T1"), team("GEN")))

        every { tournamentPollingDao.findInProgressTournaments() } returns listOf(tournament)
        every { tournamentRepository.getReferenceById(tournament.id) } returns tournament
        coEvery { apiClientPort.fetchMatches(league.leagueApiId) } returns listOf(event)
        stubUpsertMatches()
        every { clubProfileRepository.findByAbbreviationIn(setOf("T1", "GEN")) } returns
            listOf(clubProfile("T1"), clubProfile("GEN"))
        every { matchPollingDao.upsertParticipants(any<List<MatchParticipant>>()) } returns Unit

        service.syncUpcomingMatches()

        verify(exactly = 0) { clubProfileRepository.saveAll(any<List<ClubProfile>>()) }
    }

    @Test
    fun `creates new club profiles only for unregistered teams, in a single batch`() = runBlocking {
        val league = league()
        val tournament = tournament(league)
        val event = scheduleEvent("match-new", teams = listOf(team("T1"), team("NEW", name = "New Team")))

        every { tournamentPollingDao.findInProgressTournaments() } returns listOf(tournament)
        every { tournamentRepository.getReferenceById(tournament.id) } returns tournament
        coEvery { apiClientPort.fetchMatches(league.leagueApiId) } returns listOf(event)
        stubUpsertMatches()
        every { clubProfileRepository.findByAbbreviationIn(setOf("T1", "NEW")) } returns listOf(clubProfile("T1"))
        every { clubProfileRepository.saveAll(any<List<ClubProfile>>()) } answers { firstArg() }
        every { matchPollingDao.upsertParticipants(any<List<MatchParticipant>>()) } returns Unit

        service.syncUpcomingMatches()

        verify(exactly = 1) {
            clubProfileRepository.saveAll(
                match<List<ClubProfile>> { profiles ->
                    profiles.size == 1 && profiles[0].abbreviation == "NEW" && profiles[0].clubName == "New Team"
                },
            )
        }
    }

    @Test
    fun `saves a match participant for every team on the match, in one batch`() = runBlocking {
        val league = league()
        val tournament = tournament(league)
        val event = scheduleEvent("match-new", teams = listOf(team("T1"), team("GEN")))

        every { tournamentPollingDao.findInProgressTournaments() } returns listOf(tournament)
        every { tournamentRepository.getReferenceById(tournament.id) } returns tournament
        coEvery { apiClientPort.fetchMatches(league.leagueApiId) } returns listOf(event)
        stubUpsertMatches()
        every { clubProfileRepository.findByAbbreviationIn(setOf("T1", "GEN")) } returns
            listOf(clubProfile("T1"), clubProfile("GEN"))
        val savedParticipants = slot<List<MatchParticipant>>()
        every { matchPollingDao.upsertParticipants(capture(savedParticipants)) } returns Unit

        service.syncUpcomingMatches()

        assertEquals(2, savedParticipants.captured.size)
        assertEquals(setOf("T1", "GEN"), savedParticipants.captured.map { it.clubProfile.abbreviation }.toSet())
        assertTrue(savedParticipants.captured.all { it.match.matchApiId == "match-new" })
    }

    @Test
    fun `does not throw when an existing club profile has no linked club`() = runBlocking {
        val league = league()
        val tournament = tournament(league)
        val event = scheduleEvent("match-new", teams = listOf(team("T1")))

        every { tournamentPollingDao.findInProgressTournaments() } returns listOf(tournament)
        every { tournamentRepository.getReferenceById(tournament.id) } returns tournament
        coEvery { apiClientPort.fetchMatches(league.leagueApiId) } returns listOf(event)
        stubUpsertMatches()
        every { clubProfileRepository.findByAbbreviationIn(setOf("T1")) } returns listOf(clubProfile("T1", club = null))
        val savedParticipants = slot<List<MatchParticipant>>()
        every { matchPollingDao.upsertParticipants(capture(savedParticipants)) } returns Unit

        service.syncUpcomingMatches()

        assertNull(savedParticipants.captured.single().club)
    }

    @Test
    fun `re-polling the same match does not duplicate the upsertMatches input across two runs`() = runBlocking {
        val league = league()
        val tournament = tournament(league)
        val event = scheduleEvent("match-repeat")

        every { tournamentPollingDao.findInProgressTournaments() } returns listOf(tournament)
        every { tournamentRepository.getReferenceById(tournament.id) } returns tournament
        coEvery { apiClientPort.fetchMatches(league.leagueApiId) } returns listOf(event)
        stubUpsertMatches()
        every { clubProfileRepository.findByAbbreviationIn(any()) } returns listOf(clubProfile("T1"), clubProfile("GEN"))
        every { matchPollingDao.upsertParticipants(any<List<MatchParticipant>>()) } returns Unit

        service.syncUpcomingMatches()
        service.syncUpcomingMatches()

        verify(exactly = 2) { matchPollingDao.upsertMatches(any<List<Match>>()) }
    }
}
