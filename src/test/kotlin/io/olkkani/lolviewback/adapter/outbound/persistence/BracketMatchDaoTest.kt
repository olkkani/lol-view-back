package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.client.bracket.dto.PandaScoreMatch
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.BracketMatchDao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper

@Testcontainers
@SpringBootTest
@Transactional
class BracketMatchDaoTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var bracketMatchDao: BracketMatchDao

    private val objectMapper = JsonMapper.builder().build()

    private fun sampleMatch(status: String = "not_started", winnerId: Long? = null) =
        PandaScoreMatch(
            id = 1642162L,
            name = "Lower bracket round 1: BFX vs DK",
            status = status,
            slug = "2026-09-03-572d56d4",
            winnerId = winnerId,
            winnerType = if (winnerId != null) "Team" else null,
            tournamentId = 21722L,
            beginAt = null,
            endAt = null,
            scheduledAt = "2026-09-03T08:00:00Z",
            previousMatches = objectMapper.readTree("""[{"type":"loser","match_id":1642153}]"""),
            opponents = objectMapper.readTree("""[{"type":"Team","opponent":{"id":134115}}]"""),
            results = objectMapper.readTree("[]"),
        )

    @Test
    fun `first sync inserts a new row`() {
        val saved = bracketMatchDao.upsertMatches("115548147890329817", listOf(sampleMatch()))

        assertEquals(1, saved.size)
        assertEquals("not_started", saved[0].status)
    }

    @Test
    fun `second sync against the same match_id updates the row in place, not duplicating`() {
        bracketMatchDao.upsertMatches("115548147890329817", listOf(sampleMatch(status = "not_started", winnerId = null)))

        val resynced =
            bracketMatchDao.upsertMatches(
                "115548147890329817",
                listOf(sampleMatch(status = "finished", winnerId = 132531L)),
            )

        assertEquals(1, resynced.size, "second sync must update the existing row, not add a duplicate")
        assertEquals("finished", resynced[0].status)
        assertEquals(132531L, resynced[0].winnerId)
    }
}
