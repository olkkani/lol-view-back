package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.dao.TournamentPollingDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LogoBackdrop
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

@Testcontainers
@SpringBootTest
@Transactional
class TournamentPollingDaoTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var leagueRepository: LeagueRepository

    @Autowired
    lateinit var tournamentRepository: TournamentRepository

    @Autowired
    lateinit var tournamentPollingDao: TournamentPollingDao

    @Autowired
    lateinit var entityManager: EntityManager

    private val today = LocalDate.now()

    @Test
    fun `finds a tournament in progress within the next 7 days and maps its league`() {
        val league = leagueRepository.save(
            League(
                leagueName = "LCK",
                logoUrl = "https://example.com/lck.png",
                isActive = true,
                leagueApiId = "lck-api-id",
                logoBackdrop = LogoBackdrop.DARK,
            ),
        )
        val tournament = tournamentRepository.save(
            Tournament(
                tournamentName = "LCK Summer 2026",
                startDate = today.minusDays(30),
                endDate = today.plusDays(3),
                tournamentApiId = "lck-summer-2026",
                league = league,
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val result = tournamentPollingDao.findInProgressTournaments()
        val found = result.single { it.id == tournament.id }

        assertEquals(tournament.tournamentApiId, found.tournamentApiId)
        assertEquals(league.id, found.league.id)
        assertEquals(league.leagueApiId, found.league.leagueApiId)
        assertEquals(LogoBackdrop.DARK, found.league.logoBackdrop)
    }

    @Test
    fun `excludes tournaments that have already ended or start more than 7 days from now`() {
        val league = leagueRepository.save(
            League(leagueName = "LEC", logoUrl = "https://example.com/lec.png", isActive = true, leagueApiId = "lec-api-id"),
        )
        tournamentRepository.save(
            Tournament(
                tournamentName = "Ended Tournament",
                startDate = today.minusDays(60),
                endDate = today.minusDays(1),
                tournamentApiId = "ended",
                league = league,
            ),
        )
        tournamentRepository.save(
            Tournament(
                tournamentName = "Too Far Out",
                startDate = today.plusDays(30),
                endDate = today.plusDays(60),
                tournamentApiId = "too-far-out",
                league = league,
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val result = tournamentPollingDao.findInProgressTournaments()

        assertTrue(result.none { it.tournamentApiId == "ended" || it.tournamentApiId == "too-far-out" })
    }
}
