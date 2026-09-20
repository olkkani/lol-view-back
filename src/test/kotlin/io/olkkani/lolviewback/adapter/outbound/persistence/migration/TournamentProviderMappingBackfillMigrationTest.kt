package io.olkkani.lolviewback.adapter.outbound.persistence.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager

/**
 * Proves V1.2__add_provider_external_ids.sql's tournament_provider_mappings ->
 * tournament_external_ids join-backfill (LOLESPORTS + PANDASCORE rows per
 * mapping, entity_id resolved via tournaments.tournament_api_id) is correct
 * when the mapping table actually has rows at migration time.
 *
 * MigrationBackfillTest exercises V1.2 against V1.1's seed data, which ships
 * zero tournament_provider_mappings rows - that join-backfill path was
 * previously covered by a MigrationBackfillTest case that hand-inserted rows
 * *after* Flyway had already run V1.2 and re-ran the join SQL manually inline
 * to prove its shape. That case was removed when Migration B
 * (V1.3__drop_legacy_provider_columns.sql) was added to the default
 * migration chain, since tournament_provider_mappings/tournament_api_id no
 * longer exist post-chain and the test could not run inline SQL against them.
 *
 * This test restores real regression coverage for that join-backfill logic
 * by driving Flyway directly (not via Spring Boot's autoconfigured bean,
 * same as MatchDedupMigrationTest), pinning the chain to V1.1 first so the
 * mapping/tournament rows can be hand-inserted *before* V1.2 ever runs, then
 * applying V1.2 alone so its own INSERT statements perform the backfill
 * against that hand-inserted data - not a re-implementation of the migration
 * SQL, the actual migration file under test.
 */
class TournamentProviderMappingBackfillMigrationTest {

    private lateinit var postgres: PostgreSQLContainer<*>

    @BeforeEach
    fun startContainer() {
        postgres = PostgreSQLContainer("postgres:16-alpine")
        postgres.start()
    }

    @AfterEach
    fun stopContainer() {
        postgres.stop()
    }

    private fun connection(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun migrateUpTo(target: String) {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .target(target)
            .load()
            .migrate()
    }

    @Test
    fun `V1_2 backfills tournament_provider_mappings into two provider rows per mapping via tournament_api_id join`() {
        // Migrate only through V1.1 - the schema state before Migration A's backfill runs.
        // V1.1 seeds league id 1004 (LCK), reused here as the tournament's parent league.
        migrateUpTo("1.1")

        connection().use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """INSERT INTO "tournaments" ("id", "tournament_name", "tournament_api_id", "league_id")
                       VALUES (500001, 'Test Tournament', 'lolesports-abc', 1004)""",
                )
                stmt.execute(
                    """INSERT INTO "tournament_provider_mappings"
                       ("id", "lolesports_tournament_id", "pandascore_tournament_id")
                       VALUES (500002, 'lolesports-abc', 'pandascore-xyz')""",
                )
            }
        }

        // Apply V1.2 alone on top of the hand-inserted mapping/tournament rows, so its own
        // join-backfill INSERT statements (not a re-implementation) run against this data.
        migrateUpTo("1.2")

        connection().use { conn ->
            conn.createStatement().use { stmt ->
                var lolesports: String? = null
                stmt.executeQuery(
                    """SELECT "external_id" FROM "tournament_external_ids"
                       WHERE "entity_id" = 500001 AND "provider" = 'LOLESPORTS'""",
                ).use { rs ->
                    rs.next()
                    lolesports = rs.getString("external_id")
                }
                assertEquals("lolesports-abc", lolesports)

                var pandascore: String? = null
                stmt.executeQuery(
                    """SELECT "external_id" FROM "tournament_external_ids"
                       WHERE "entity_id" = 500001 AND "provider" = 'PANDASCORE'""",
                ).use { rs ->
                    rs.next()
                    pandascore = rs.getString("external_id")
                }
                assertEquals("pandascore-xyz", pandascore)

                // Exactly two rows for this tournament (LOLESPORTS + PANDASCORE), not more.
                var rowCount = -1
                stmt.executeQuery(
                    """SELECT COUNT(*) AS c FROM "tournament_external_ids" WHERE "entity_id" = 500001""",
                ).use { rs ->
                    rs.next()
                    rowCount = rs.getInt("c")
                }
                assertEquals(2, rowCount)
            }
        }
    }
}
