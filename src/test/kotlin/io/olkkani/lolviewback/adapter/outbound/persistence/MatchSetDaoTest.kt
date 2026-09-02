package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.dao.ClubProfileRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchSetDao
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchSetRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Club
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubProfile
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchSet
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Testcontainers
@SpringBootTest
class MatchSetDaoTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var matchRepository: MatchRepository

    @Autowired
    lateinit var tournamentRepository: TournamentRepository

    @Autowired
    lateinit var leagueRepository: LeagueRepository

    @Autowired
    lateinit var clubRepository: ClubRepository

    @Autowired
    lateinit var clubProfileRepository: ClubProfileRepository

    @Autowired
    lateinit var matchSetRepository: MatchSetRepository

    @Autowired
    lateinit var matchSetDao: MatchSetDao

    private val kst = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 8, 31)

    private fun tournament(): Tournament {
        val league = leagueRepository.save(
            League(leagueName = "LCK", logoUrl = "https://example.com/lck.png", isActive = true, leagueApiId = "lck-api-id"),
        )
        return tournamentRepository.save(
            Tournament(
                tournamentName = "LCK Summer 2026",
                startDate = LocalDate.of(2026, 6, 1),
                endDate = LocalDate.of(2026, 8, 31),
                tournamentApiId = "lck-summer-2026",
                league = league,
            ),
        )
    }

    private fun match(t: Tournament, apiId: String) = matchRepository.save(
        Match(
            startTime = ZonedDateTime.now(kst),
            matchType = MatchType.BO3,
            matchState = MatchState.IN_PROGRESS,
            matchLabel = "W1",
            matchApiId = apiId,
            tournament = t,
        ),
    )

    @Test
    fun `wins are not fan-out inflated by a club with multiple profile versions and abbreviation reflects the active one`() {
        val t = tournament()
        val m = match(t, "m1")
        val club = clubRepository.save(Club(isActive = true))

        // Past (inactive) profile — must not cause a duplicate join row.
        clubProfileRepository.save(
            ClubProfile(
                clubName = "SKT T1",
                abbreviation = "SKT",
                logoUrl = "https://example.com/skt.png",
                effectiveFrom = LocalDate.of(2015, 1, 1),
                effectiveTo = LocalDate.of(2020, 1, 1),
                club = club,
            ),
        )
        // Currently active profile.
        clubProfileRepository.save(
            ClubProfile(
                clubName = "T1",
                abbreviation = "T1",
                logoUrl = "https://example.com/t1.png",
                effectiveFrom = LocalDate.of(2020, 1, 1),
                effectiveTo = LocalDate.of(2099, 1, 1),
                club = club,
            ),
        )

        matchSetRepository.save(MatchSet(setApiId = "s1", setNumber = 1, club = club, match = m))
        matchSetRepository.save(MatchSet(setApiId = "s2", setNumber = 2, club = club, match = m))

        val result = matchSetDao.findWinCountsByMatchIdIn(listOf(m.id), mapOf(m.id to today))

        assertEquals(1, result.size)
        assertEquals(2, result[0].wins)
        assertEquals("T1", result[0].abbreviation)
    }

    @Test
    fun `club with no active profile still contributes its win count with a null abbreviation`() {
        val t = tournament()
        val m = match(t, "m2")
        val club = clubRepository.save(Club(isActive = true))

        matchSetRepository.save(MatchSet(setApiId = "s3", setNumber = 1, club = club, match = m))

        val result = matchSetDao.findWinCountsByMatchIdIn(listOf(m.id), mapOf(m.id to today))

        assertEquals(1, result.size)
        assertEquals(1, result[0].wins)
        assertNull(result[0].abbreviation)
    }

    @Test
    fun `empty match id list returns an empty result without querying`() {
        val result = matchSetDao.findWinCountsByMatchIdIn(emptyList(), emptyMap())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `abbreviation reflects the club's branding as of the match's own date, not today`() {
        val t = tournament()
        val m = match(t, "m3")
        val club = clubRepository.save(Club(isActive = true))

        // Old branding, active only during the match's era.
        clubProfileRepository.save(
            ClubProfile(
                clubName = "SKT T1",
                abbreviation = "SKT",
                logoUrl = "https://example.com/skt.png",
                effectiveFrom = LocalDate.of(2015, 1, 1),
                effectiveTo = LocalDate.of(2020, 1, 1),
                club = club,
            ),
        )
        // Current branding, active only from 2020 onward — i.e. active "today" (2026-08-31).
        clubProfileRepository.save(
            ClubProfile(
                clubName = "T1",
                abbreviation = "T1",
                logoUrl = "https://example.com/t1.png",
                effectiveFrom = LocalDate.of(2020, 1, 1),
                effectiveTo = LocalDate.of(2099, 1, 1),
                club = club,
            ),
        )

        matchSetRepository.save(MatchSet(setApiId = "s4", setNumber = 1, club = club, match = m))

        val matchPlayedDate = LocalDate.of(2017, 6, 1)
        val result = matchSetDao.findWinCountsByMatchIdIn(listOf(m.id), mapOf(m.id to matchPlayedDate))

        assertEquals(1, result.size)
        assertEquals("SKT", result[0].abbreviation)
    }
}
