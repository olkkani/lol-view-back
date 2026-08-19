package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.LogoBackdrop
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchType
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
@DataJpaTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
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

    @Autowired
    lateinit var entityManager: EntityManager

    @Autowired
    lateinit var sessionFactory: SessionFactory

    private val kst = ZoneId.of("Asia/Seoul")

    private fun tournament(): Tournament {
        val league = leagueRepository.save(
            League(
                leagueName = "LCK",
                logoUrl = "https://example.com/lck.png",
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
    fun `findByStartTime range returns matches within range only`() {
        val t = tournament()
        val inRange = matchRepository.save(match(ZonedDateTime.of(2026, 8, 12, 10, 0, 0, 0, kst), t))
        matchRepository.save(match(ZonedDateTime.of(2026, 8, 11, 23, 0, 0, 0, kst), t))
        matchRepository.save(match(ZonedDateTime.of(2026, 8, 13, 0, 0, 1, 0, kst), t))

        val start = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst)
        val end = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst)

        val result = matchRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(start, end)

        assertEquals(1, result.size)
        assertEquals(inRange.id, result[0].id)
    }

    @Test
    fun `findByStartTime range is start-inclusive and end-exclusive`() {
        val t = tournament()
        val start = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst)
        val end = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst)

        val atStart = matchRepository.save(match(start, t))
        val atEnd = matchRepository.save(match(end, t))

        val result = matchRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(start, end)

        assertEquals(listOf(atStart.id), result.map { it.id })
        assertFalse(result.any { it.id == atEnd.id })
    }

    @Test
    fun `a match on a shared day boundary belongs to exactly one of two adjacent ranges`() {
        val t = tournament()
        val day1Start = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst)
        val day2Start = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst)
        val day3Start = ZonedDateTime.of(2026, 8, 14, 0, 0, 0, 0, kst)

        // 15:00Z == 00:00 KST — the most common real start time in the upstream feed.
        val boundaryMatch = matchRepository.save(match(day2Start, t))

        val day1 = matchRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(day1Start, day2Start)
        val day2 = matchRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(day2Start, day3Start)

        assertFalse(day1.any { it.id == boundaryMatch.id })
        assertEquals(listOf(boundaryMatch.id), day2.map { it.id })
    }

    @Test
    fun `findByStartTime range fetch-joins tournament and league in a single query`() {
        val t1 = tournament()
        val t2 = tournament()
        matchRepository.save(match(ZonedDateTime.of(2026, 8, 12, 10, 0, 0, 0, kst), t1))
        matchRepository.save(match(ZonedDateTime.of(2026, 8, 12, 12, 0, 0, 0, kst), t2))
        matchRepository.flush()
        entityManager.clear()

        val start = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst)
        val end = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst)

        sessionFactory.statistics.clear()
        val result = matchRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(start, end)
        result.forEach { assertEquals("LCK", it.tournament.league.leagueName) }

        assertEquals(2, result.size)
        assertEquals(1, sessionFactory.statistics.prepareStatementCount)
    }

    @Test
    fun `League logoBackdrop persists and reads back as the same enum constant`() {
        val saved = leagueRepository.save(
            League(
                leagueName = "LCK",
                logoUrl = "https://example.com/lck.png",
                isActive = true,
                leagueApiId = "lck-api-id-2",
                logoBackdrop = LogoBackdrop.DARK,
            ),
        )
        leagueRepository.flush()
        entityManager.clear()

        val found = leagueRepository.findById(saved.id!!).get()

        assertEquals(LogoBackdrop.DARK, found.logoBackdrop)
    }

    @Test
    fun `League logoBackdrop round-trips as null when uncurated`() {
        val saved = leagueRepository.save(
            League(
                leagueName = "LCK",
                logoUrl = "https://example.com/lck.png",
                isActive = true,
                leagueApiId = "lck-api-id-3",
                logoBackdrop = null,
            ),
        )
        leagueRepository.flush()
        entityManager.clear()

        val found = leagueRepository.findById(saved.id!!).get()

        assertNull(found.logoBackdrop)
    }

    @Test
    fun `KST ZonedDateTime survives the persistence round-trip`() {
        val t = tournament()
        val startTime = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst)
        val saved = matchRepository.save(match(startTime, t))
        matchRepository.flush()
        entityManager.clear()

        val justAfterStart = startTime.plusNanos(1_000_000)
        val result = matchRepository
            .findByStartTimeGreaterThanEqualAndStartTimeLessThan(startTime, justAfterStart)

        assertEquals(listOf(saved.id), result.map { it.id })
        // Same instant regardless of the zone the value is rendered in.
        assertEquals(startTime.toInstant(), result[0].startTime.toInstant())
        assertEquals(
            startTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime(),
            result[0].startTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime(),
        )
    }
}
