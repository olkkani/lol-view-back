package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.client.bracket.dto.PandaScoreMatch
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.BracketMatchDao
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.BracketStageDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.BracketStage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
class BracketStageDaoTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var bracketStageDao: BracketStageDao

    @Autowired
    lateinit var bracketMatchDao: BracketMatchDao

    private val objectMapper = JsonMapper.builder().build()

    private fun sampleStage(
        tournamentId: String = "115548147890329817",
        name: String = "Playoffs",
        format: String = "DOUBLE_ELIMINATION",
        ordinal: Int = 1,
        config: String? = null,
    ) = BracketStage(
        tournamentId = tournamentId,
        name = name,
        format = format,
        ordinal = ordinal,
        config = config,
    )

    @Test
    fun `insert persists a stage and find returns it`() {
        bracketStageDao.upsert(sampleStage())

        val found = bracketStageDao.findByTournamentId("115548147890329817")

        assertEquals(1, found.size)
        assertEquals("Playoffs", found[0].name)
        assertEquals("DOUBLE_ELIMINATION", found[0].format)
        assertEquals(1, found[0].ordinal)
        assertNull(found[0].config)
    }

    @Test
    fun `find returns stages ordered by ordinal`() {
        bracketStageDao.upsert(sampleStage(name = "Playoffs", ordinal = 2))
        bracketStageDao.upsert(sampleStage(name = "Play-In", ordinal = 1))

        val found = bracketStageDao.findByTournamentId("115548147890329817")

        assertEquals(listOf("Play-In", "Playoffs"), found.map { it.name })
    }

    @Test
    fun `config JSONB round-trips as raw JSON text`() {
        bracketStageDao.upsert(
            sampleStage(
                format = "SWISS",
                config = """{"rounds":5,"pairing":"danish","tiebreak":"buchholz"}""",
            ),
        )

        val found = bracketStageDao.findByTournamentId("115548147890329817")

        // Postgres JSONB canonicalizes whitespace on storage (adds a space after
        // ':' and ','), so the round-tripped text isn't byte-identical to the
        // input even though it's the same JSON value.
        assertEquals("""{"rounds": 5, "pairing": "danish", "tiebreak": "buchholz"}""", found[0].config)
    }

    @Test
    fun `upsert rejects an unrecognized format value`() {
        // BracketStageDao is a Spring @Repository, so PersistenceExceptionTranslationInterceptor
        // wraps the IllegalArgumentException thrown by `require()` in an
        // InvalidDataAccessApiUsageException. The cause is the original exception.
        val exception =
            assertThrows(RuntimeException::class.java) {
                bracketStageDao.upsert(sampleStage(format = "SWISSS"))
            }
        val cause = exception.cause
        assertEquals(IllegalArgumentException::class.java, cause?.javaClass)
        assertEquals(
            "Unrecognized bracket stage format: SWISSS " +
                "(expected one of: ROUND_ROBIN, SWISS, SINGLE_ELIMINATION, DOUBLE_ELIMINATION)",
            cause?.message,
        )
    }

    @Test
    fun `upsert accepts each of the four valid format values`() {
        listOf("ROUND_ROBIN", "SWISS", "SINGLE_ELIMINATION", "DOUBLE_ELIMINATION")
            .forEachIndexed { index, format ->
                bracketStageDao.upsert(sampleStage(name = format, format = format, ordinal = index))
            }

        val found = bracketStageDao.findByTournamentId("115548147890329817")

        assertEquals(4, found.size)
    }

    @Test
    fun `real LCK 2026 Playoffs double-elimination matches join to their curated stage`() {
        val stage =
            bracketStageDao.upsert(
                BracketStage(
                    tournamentId = "115548147890329817",
                    name = "Playoffs",
                    format = "DOUBLE_ELIMINATION",
                    ordinal = 1,
                    config = null,
                ),
            )

        val grandFinal =
            PandaScoreMatch(
                id = 1642158L,
                name = "Grand final: GEN vs HLE",
                status = "finished",
                slug = "gen-g-2026-09-13",
                winnerId = 2882L,
                winnerType = "Team",
                tournamentId = 21722L,
                beginAt = "2026-09-13T05:22:41Z",
                endAt = "2026-09-13T08:48:04Z",
                scheduledAt = "2026-09-13T05:00:00Z",
                previousMatches =
                    objectMapper.readTree(
                        """[{"type":"winner","match_id":1642157},{"type":"winner","match_id":1642159}]""",
                    ),
                opponents = objectMapper.readTree("""[{"type":"Team","opponent":{"id":2882}},{"type":"Team","opponent":{"id":2883}}]"""),
                results = objectMapper.readTree("""[{"team_id":2883,"score":1},{"team_id":2882,"score":3}]"""),
            )
        val lowerBracketRound1 =
            PandaScoreMatch(
                id = 1642162L,
                name = "Lower bracket round 1: BFX vs DK",
                status = "finished",
                slug = "2026-09-03-572d56d4-cdb7-4e13-bebb-583b92cb5e62",
                winnerId = 132531L,
                winnerType = "Team",
                tournamentId = 21722L,
                beginAt = "2026-09-03T08:02:42Z",
                endAt = "2026-09-03T12:24:59Z",
                scheduledAt = "2026-09-03T08:00:00Z",
                previousMatches =
                    objectMapper.readTree(
                        """[{"type":"loser","match_id":1642153},{"type":"loser","match_id":1642154}]""",
                    ),
                opponents = objectMapper.readTree("""[{"type":"Team","opponent":{"id":134115}},{"type":"Team","opponent":{"id":132531}}]"""),
                results = objectMapper.readTree("""[{"team_id":132531,"score":3},{"team_id":134115,"score":2}]"""),
            )

        bracketMatchDao.upsertMatches(
            "115548147890329817",
            listOf(grandFinal, lowerBracketRound1),
            stageId = stage.id,
        )

        val matches = bracketMatchDao.findByTournamentId("115548147890329817")

        assertEquals(2, matches.size)
        assertEquals(setOf(stage.id), matches.map { it.stageId }.toSet())

        val loadedStage = bracketStageDao.findByTournamentId("115548147890329817").single()
        assertEquals("DOUBLE_ELIMINATION", loadedStage.format)
    }
}
