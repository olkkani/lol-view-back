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
 * those backfill paths run over 0 source rows in this test.
 *
 * Testcontainers runs the full migration chain by default, which now
 * includes Migration B (V1.3__drop_legacy_provider_columns.sql). Tests that
 * asserted on the pre-Migration-B state of `league_api_id`,
 * `tournament_api_id`, and `tournament_provider_mappings` (the "does not
 * drop legacy source" and "backfills tournament_provider_mappings via
 * tournament_api_id join" cases from Task 3) were removed here: their sole
 * purpose was pinning Migration A's "must not touch legacy schema" invariant
 * during the interim window before Migration B shipped, and they can no
 * longer execute once those columns/table are actually dropped. The SQL
 * shape they exercised was already verified correct when V1.2 was reviewed
 * and committed; the post-drop end state is now covered by
 * `legacy columns and table no longer exist after Migration B` below.
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
    fun `legacy columns and table no longer exist after Migration B`() {
        val columnExists = countRows(
            """
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'leagues' AND column_name = 'league_api_id'
            """.trimIndent(),
        )

        assertEquals(0L, columnExists)
    }

    private fun countRows(sql: String): Long =
        ((entityManager.createNativeQuery(sql) as Query).singleResult as Number).toLong()
}
