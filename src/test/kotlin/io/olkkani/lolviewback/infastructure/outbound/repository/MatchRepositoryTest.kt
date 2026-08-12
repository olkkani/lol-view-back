package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchType
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
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
class MatchRepositoryTest {

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

    private val kst = ZoneId.of("Asia/Seoul")

    private fun tournament(): Tournament {
        val league = leagueRepository.save(
            League(
                leagueName = "LCK",
                leagueLogoUrl = "https://example.com/lck.png",
                isActive = true,
                leagueApiId = "lck-api-id",
            ),
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

    private fun match(startTime: ZonedDateTime, tournament: Tournament) = Match(
        startTime = startTime,
        matchType = MatchType.BO3,
        matchState = MatchState.SCHEDULED,
        matchLabel = "Week 1",
        matchApiId = "match-${startTime.toInstant().toEpochMilli()}",
        tournament = tournament,
    )

    @Test
    fun `findByStartTimeBetween returns matches within range only`() {
        val t = tournament()
        val inRange = matchRepository.save(match(ZonedDateTime.of(2026, 8, 12, 10, 0, 0, 0, kst), t))
        matchRepository.save(match(ZonedDateTime.of(2026, 8, 11, 23, 0, 0, 0, kst), t))
        matchRepository.save(match(ZonedDateTime.of(2026, 8, 13, 0, 0, 1, 0, kst), t))

        val start = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst)
        val end = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst)

        val result = matchRepository.findByStartTimeBetween(start, end)

        assertEquals(1, result.size)
        assertEquals(inRange.id, result[0].id)
    }
}
