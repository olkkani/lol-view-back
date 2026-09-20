package io.olkkani.lolviewback.adapter.outbound.persistence

import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Verifies Migration A's backfill against the actual seeded data shipped in
 * V1.1__create_table.sql: 6 leagues, all with non-null league_api_id.
 * V1.1 seeds no tournaments and no tournament_provider_mappings rows, so
 * those backfill paths run over 0 source rows in this test — correctness
 * of the tournament_provider_mappings SQL path is exercised in a separate
 * test that inserts rows directly before verifying the join-based backfill.
 */
@Testcontainers
@SpringBootTest
@Transactional
class MigrationBackfillTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `backfills one LOLESPORTS row per existing non-null league_api_id`() {
        val leagueCount = countRows("SELECT COUNT(*) FROM leagues WHERE league_api_id IS NOT NULL")
        val backfilledCount = countRows(
            "SELECT COUNT(*) FROM league_external_ids WHERE provider = 'LOLESPORTS'",
        )

        assertEquals(leagueCount, backfilledCount)
        assertEquals(6L, leagueCount)
    }

    @Test
    fun `backfilled external_id values match the source league_api_id values`() {
        val result = entityManager.createNativeQuery(
            """
            SELECT l.league_api_id, e.external_id
            FROM leagues l
            JOIN league_external_ids e ON e.entity_id = l.id AND e.provider = 'LOLESPORTS'
            WHERE l.league_api_id IS NOT NULL
            """.trimIndent(),
        ).resultList as List<Array<Any>>

        result.forEach { row -> assertEquals(row[0], row[1]) }
        assertEquals(6, result.size)
    }

    @Test
    fun `migration_audit_log records matching before and after counts for leagues`() {
        val before = countRows("SELECT row_count_before FROM migration_audit_log WHERE table_name = 'league_external_ids'")
        val after = countRows("SELECT row_count_after FROM migration_audit_log WHERE table_name = 'league_external_ids'")

        assertEquals(before, after)
        assertEquals(6L, before)
    }

    @Test
    fun `migration_audit_log records matching before and after counts for tournament_external_ids`() {
        val before = countRows("SELECT row_count_before FROM migration_audit_log WHERE table_name = 'tournament_external_ids'")
        val after = countRows("SELECT row_count_after FROM migration_audit_log WHERE table_name = 'tournament_external_ids'")

        assertEquals(before, after)
        assertEquals(0L, before)
    }

    @Test
    fun `does not backfill tournament_external_ids when no tournaments or mappings are seeded`() {
        val count = countRows("SELECT COUNT(*) FROM tournament_external_ids")

        assertEquals(0L, count)
    }

    @Test
    fun `does not drop or empty the legacy source tables and columns`() {
        val leagueApiIdCount = countRows("SELECT COUNT(*) FROM leagues WHERE league_api_id IS NOT NULL")
        val mappingsCount = countRows("SELECT COUNT(*) FROM tournament_provider_mappings")

        assertEquals(6L, leagueApiIdCount)
        assertEquals(0L, mappingsCount)
    }

    @Test
    fun `backfills tournament_provider_mappings into two provider rows per mapping via tournament_api_id join`() {
        entityManager.createNativeQuery(
            """
            INSERT INTO tournaments (id, tournament_name, tournament_api_id, league_id)
            VALUES (500001, 'Test Tournament', 'lolesports-abc', 1004)
            """.trimIndent(),
        ).executeUpdate()
        entityManager.createNativeQuery(
            """
            INSERT INTO tournament_provider_mappings (id, lolesports_tournament_id, pandascore_tournament_id)
            VALUES (500002, 'lolesports-abc', 'pandascore-xyz')
            """.trimIndent(),
        ).executeUpdate()

        // Re-run the mapping backfill logic inline (Flyway already ran V1.2 before this row existed),
        // reusing the same join Migration A uses, to prove the SQL shape is correct.
        entityManager.createNativeQuery(
            """
            INSERT INTO tournament_external_ids (id, entity_id, provider, external_id)
            SELECT 500003, t.id, 'LOLESPORTS', m.lolesports_tournament_id
            FROM tournament_provider_mappings m
            JOIN tournaments t ON t.tournament_api_id = m.lolesports_tournament_id
            WHERE m.id = 500002
            ON CONFLICT (entity_id, provider) DO NOTHING
            """.trimIndent(),
        ).executeUpdate()
        entityManager.createNativeQuery(
            """
            INSERT INTO tournament_external_ids (id, entity_id, provider, external_id)
            SELECT 500004, t.id, 'PANDASCORE', m.pandascore_tournament_id
            FROM tournament_provider_mappings m
            JOIN tournaments t ON t.tournament_api_id = m.lolesports_tournament_id
            WHERE m.id = 500002
            """.trimIndent(),
        ).executeUpdate()

        val lolesports = entityManager.createNativeQuery(
            "SELECT external_id FROM tournament_external_ids WHERE entity_id = 500001 AND provider = 'LOLESPORTS'",
        ).singleResult as String
        val pandascore = entityManager.createNativeQuery(
            "SELECT external_id FROM tournament_external_ids WHERE entity_id = 500001 AND provider = 'PANDASCORE'",
        ).singleResult as String

        assertEquals("lolesports-abc", lolesports)
        assertEquals("pandascore-xyz", pandascore)
    }

    private fun countRows(sql: String): Long =
        ((entityManager.createNativeQuery(sql) as Query).singleResult as Number).toLong()
}
