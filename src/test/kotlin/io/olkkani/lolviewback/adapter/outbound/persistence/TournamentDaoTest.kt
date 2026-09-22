package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.dao.TournamentDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.TournamentExternalId
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
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
class TournamentDaoTest {

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
    lateinit var tournamentExternalIdRepository: TournamentExternalIdRepository

    @Autowired
    lateinit var tournamentDao: TournamentDao

    @Autowired
    lateinit var entityManager: EntityManager

    private fun league() = leagueRepository.save(
        League(leagueName = "LCK", logoUrl = "https://example.com/lck.png", isActive = true),
    )

    private fun tournament(league: League) = tournamentRepository.save(
        Tournament(
            tournamentName = "LCK Summer 2026",
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(30),
            league = league,
        ),
    )

    @Test
    fun `returns only tournaments with both LOLESPORTS and PANDASCORE mappings`() {
        val bothMapped = tournament(league())
        val lolesportsOnly = tournament(league())

        tournamentExternalIdRepository.save(
            TournamentExternalId(entityId = bothMapped.id, provider = Provider.LOLESPORTS, externalId = "lol-1"),
        )
        tournamentExternalIdRepository.save(
            TournamentExternalId(entityId = bothMapped.id, provider = Provider.PANDASCORE, externalId = "panda-1"),
        )
        tournamentExternalIdRepository.save(
            TournamentExternalId(entityId = lolesportsOnly.id, provider = Provider.LOLESPORTS, externalId = "lol-2"),
        )
        entityManager.flush()
        entityManager.clear()

        val result = tournamentDao.findTournamentIdsWithBothProviders(Provider.LOLESPORTS, Provider.PANDASCORE)

        assertEquals(listOf(bothMapped.id), result)
    }

    @Test
    fun `returns an empty list when no tournament has both mappings`() {
        val lolesportsOnly = tournament(league())
        tournamentExternalIdRepository.save(
            TournamentExternalId(entityId = lolesportsOnly.id, provider = Provider.LOLESPORTS, externalId = "lol-1"),
        )
        entityManager.flush()
        entityManager.clear()

        val result = tournamentDao.findTournamentIdsWithBothProviders(Provider.LOLESPORTS, Provider.PANDASCORE)

        assertEquals(emptyList<Long>(), result)
    }
}
