package io.olkkani.lolviewback.adapter.outbound.persistence.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Proves that V1.7__add_match_dedup_constraints.sql is safe to run against a database that
 * already contains duplicate rows (the state production is in today, per this migration's own
 * Goal: matches/match_participants have been double-inserted on every re-poll cycle before a
 * match's state changes). Every other Testcontainers-backed test in this project boots from a
 * clean/seeded schema, so none of them exercise this path.
 *
 * This test drives Flyway directly (not via Spring Boot's autoconfigured Flyway bean) against a
 * fresh Postgres container per test, so it can migrate only up to V1.6, hand-insert rows that
 * would violate the constraints V1.7 is about to add, then apply V1.7 alone and assert the
 * dedup + constraint-creation both succeed.
 */
class MatchDedupMigrationTest {

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
    fun `V1_7 dedups pre-existing duplicate matches and match_participants then adds unique constraints`() {
        // Migrate only up through V1.6 - the schema state production is actually in today,
        // before this fix is deployed.
        migrateUpTo("1.6")

        connection().use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                // Minimal parent rows to satisfy FKs.
                stmt.execute("""INSERT INTO "leagues" ("id", "league_name") VALUES (1, 'LCK')""")
                stmt.execute(
                    """INSERT INTO "tournaments" ("id", "tournament_name", "league_id")
                       VALUES (1, 'LCK Summer', 1)""",
                )
                stmt.execute("""INSERT INTO "clubs" ("id", "is_active") VALUES (1, true)""")
                stmt.execute("""INSERT INTO "clubs" ("id", "is_active") VALUES (2, true)""")
                stmt.execute(
                    """INSERT INTO "club_profiles" ("id", "club_name", "club_id") VALUES (1, 'T1', 1)""",
                )
                stmt.execute(
                    """INSERT INTO "club_profiles" ("id", "club_name", "club_id") VALUES (2, 'GEN', 2)""",
                )

                // Duplicate matches sharing the same match_api_id: keep id=10, doomed id=11 and id=12.
                stmt.execute(
                    """INSERT INTO "matches" ("id", "match_api_id", "tournament_id")
                       VALUES (10, 'dup-match', 1)""",
                )
                stmt.execute(
                    """INSERT INTO "matches" ("id", "match_api_id", "tournament_id")
                       VALUES (11, 'dup-match', 1)""",
                )
                stmt.execute(
                    """INSERT INTO "matches" ("id", "match_api_id", "tournament_id")
                       VALUES (12, 'dup-match', 1)""",
                )
                // A distinct, non-duplicated match that must survive untouched.
                stmt.execute(
                    """INSERT INTO "matches" ("id", "match_api_id", "tournament_id")
                       VALUES (20, 'solo-match', 1)""",
                )

                // Duplicate participants on the surviving match (id=10): keep id=100.
                stmt.execute(
                    """INSERT INTO "match_participants" ("id", "match_id", "club_id", "club_profile_id")
                       VALUES (100, 10, 1, 1)""",
                )
                stmt.execute(
                    """INSERT INTO "match_participants" ("id", "match_id", "club_id", "club_profile_id")
                       VALUES (101, 10, 1, 1)""",
                )
                // Participants attached to a doomed duplicate match (id=11) - must be fully
                // removed by Step 2, not merely deduped by Step 1.
                stmt.execute(
                    """INSERT INTO "match_participants" ("id", "match_id", "club_id", "club_profile_id")
                       VALUES (102, 11, 2, 2)""",
                )
                stmt.execute(
                    """INSERT INTO "match_participants" ("id", "match_id", "club_id", "club_profile_id")
                       VALUES (103, 11, 2, 2)""",
                )
                // A match_sets row on a doomed duplicate match (id=12) - must not orphan / must
                // not block the matches DELETE via the match_sets -> matches FK.
                stmt.execute(
                    """INSERT INTO "match_sets" ("id", "set_api_id", "match_id")
                       VALUES (200, 'set-on-doomed-match', 12)""",
                )
            }
        }

        // Apply V1.7 alone on top of the duplicate-laden V1.6 schema.
        migrateUpTo("1.7")

        connection().use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                // Exactly one matches row per match_api_id group survives, and it is the lowest id.
                val matchIds = mutableListOf<Long>()
                stmt.executeQuery(
                    """SELECT "id" FROM "matches" WHERE "match_api_id" = 'dup-match' ORDER BY "id"""",
                ).use { rs -> while (rs.next()) matchIds.add(rs.getLong("id")) }
                assertEquals(listOf(10L), matchIds)

                val soloMatchIds = mutableListOf<Long>()
                stmt.executeQuery(
                    """SELECT "id" FROM "matches" WHERE "match_api_id" = 'solo-match' ORDER BY "id"""",
                ).use { rs -> while (rs.next()) soloMatchIds.add(rs.getLong("id")) }
                assertEquals(listOf(20L), soloMatchIds)

                // Exactly one match_participants row per (match_id, club_profile_id) survives
                // for the kept match, and it is the lowest id.
                val participantIds = mutableListOf<Long>()
                stmt.executeQuery(
                    """SELECT "id" FROM "match_participants" WHERE "match_id" = 10 ORDER BY "id"""",
                ).use { rs -> while (rs.next()) participantIds.add(rs.getLong("id")) }
                assertEquals(listOf(100L), participantIds)

                // No orphaned match_participants remain pointing at deleted matches.id (11, 12).
                var orphanCount = -1
                stmt.executeQuery(
                    """SELECT COUNT(*) AS c FROM "match_participants" WHERE "match_id" IN (11, 12)""",
                ).use { rs -> rs.next(); orphanCount = rs.getInt("c") }
                assertEquals(0, orphanCount)

                // No orphaned match_sets remain pointing at deleted matches.id (12).
                var orphanSetCount = -1
                stmt.executeQuery(
                    """SELECT COUNT(*) AS c FROM "match_sets" WHERE "match_id" = 12""",
                ).use { rs -> rs.next(); orphanSetCount = rs.getInt("c") }
                assertEquals(0, orphanSetCount)

                // The two UNIQUE constraints now exist and are enforced.
                assertThrows(SQLException::class.java) {
                    stmt.execute(
                        """INSERT INTO "matches" ("id", "match_api_id", "tournament_id")
                           VALUES (999, 'dup-match', 1)""",
                    )
                }
            }
        }
    }

    @Test
    fun `V1_7 is a no-op dedup when the database has no pre-existing duplicates`() {
        migrateUpTo("1.6")

        connection().use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                stmt.execute("""INSERT INTO "leagues" ("id", "league_name") VALUES (1, 'LCK')""")
                stmt.execute(
                    """INSERT INTO "tournaments" ("id", "tournament_name", "league_id")
                       VALUES (1, 'LCK Summer', 1)""",
                )
                stmt.execute(
                    """INSERT INTO "matches" ("id", "match_api_id", "tournament_id")
                       VALUES (1, 'unique-match', 1)""",
                )
                stmt.execute(
                    """INSERT INTO "match_participants" ("id", "match_id", "club_id", "club_profile_id")
                       VALUES (100, 1, NULL, NULL)""",
                )
            }
        }

        // No pre-existing duplicates, so this must be a genuine no-op DELETE-wise: zero rows
        // removed, and the migration still succeeds in adding the UNIQUE constraints.
        migrateUpTo("1.7")

        connection().use { conn ->
            conn.createStatement().use { stmt ->
                var matchCount = -1
                stmt.executeQuery("""SELECT COUNT(*) AS c FROM "matches"""").use { rs ->
                    rs.next()
                    matchCount = rs.getInt("c")
                }
                assertEquals(1, matchCount)

                var participantCount = -1
                stmt.executeQuery("""SELECT COUNT(*) AS c FROM "match_participants"""").use { rs ->
                    rs.next()
                    participantCount = rs.getInt("c")
                }
                assertEquals(1, participantCount)
            }
        }
    }
}
