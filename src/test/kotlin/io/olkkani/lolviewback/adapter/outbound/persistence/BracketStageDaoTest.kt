package io.olkkani.lolviewback.adapter.outbound.persistence

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
}
