package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

@Testcontainers
@DataJpaTest
class TournamentRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var leagueRepository: LeagueRepository

    @Autowired
    lateinit var tournamentRepository: TournamentRepository

    private fun league() = leagueRepository.save(
        League(leagueName = "LCK", logoUrl = "https://example.com/lck.png", isActive = true),
    )

    @Test
    fun `saves and finds a tournament by id`() {
        val saved = tournamentRepository.save(
            Tournament(
                tournamentName = "LCK Summer 2026",
                startDate = LocalDate.now(),
                endDate = LocalDate.now().plusDays(30),
                league = league(),
            ),
        )

        val found = tournamentRepository.findAllById(listOf(saved.id))

        assertEquals(listOf(saved.id), found.map { it.id })
    }
}
