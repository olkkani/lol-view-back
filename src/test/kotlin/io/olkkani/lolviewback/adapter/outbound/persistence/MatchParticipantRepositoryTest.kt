package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Club
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubProfile
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchParticipant
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Testcontainers
@DataJpaTest
class MatchParticipantRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var matchRepository: MatchRepository

    @Autowired
    lateinit var matchParticipantRepository: MatchParticipantRepository

    @Autowired
    lateinit var tournamentRepository: TournamentRepository

    @Autowired
    lateinit var leagueRepository: LeagueRepository

    @Autowired
    lateinit var clubRepository: ClubRepository

    @Autowired
    lateinit var clubProfileRepository: ClubProfileRepository

    private val kst = ZoneId.of("Asia/Seoul")

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

    @Test
    fun `findByMatchIdIn returns participants for given match ids only`() {
        val t = tournament()
        val match1 = matchRepository.save(
            Match(startTime = ZonedDateTime.now(kst), matchType = MatchType.BO3, matchState = MatchState.SCHEDULED, matchLabel = "W1", matchApiId = "m1", tournament = t),
        )
        val match2 = matchRepository.save(
            Match(startTime = ZonedDateTime.now(kst), matchType = MatchType.BO3, matchState = MatchState.SCHEDULED, matchLabel = "W1", matchApiId = "m2", tournament = t),
        )

        val club = clubRepository.save(Club(isActive = true))
        val profile = clubProfileRepository.save(
            ClubProfile(
                clubName = "T1",
                abbreviation = "T1",
                logoUrl = "https://example.com/t1.png",
                effectiveFrom = LocalDate.of(2020, 1, 1),
                effectiveTo = LocalDate.of(2099, 12, 31),
                club = club,
            ),
        )

        val p1 = matchParticipantRepository.save(
            MatchParticipant(isWin = null, score = 0, match = match1, club = club, clubProfile = profile),
        )
        matchParticipantRepository.save(
            MatchParticipant(isWin = null, score = 0, match = match2, club = club, clubProfile = profile),
        )

        val result = matchParticipantRepository.findByMatchIdIn(listOf(match1.id!!))

        assertEquals(1, result.size)
        assertEquals(p1.id, result[0].id)
        assertTrue(result.all { it.match.id == match1.id })
    }
}
