package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchPollingDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Club
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubProfile
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchParticipant
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.ZonedDateTime

@Testcontainers
@SpringBootTest
@Transactional
class MatchPollingDaoTest {

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
    lateinit var clubRepository: ClubRepository

    @Autowired
    lateinit var clubProfileRepositoryDao: io.olkkani.lolviewback.adapter.outbound.persistence.dao.ClubProfileRepository

    @Autowired
    lateinit var matchPollingDao: MatchPollingDao

    @Autowired
    lateinit var entityManager: EntityManager

    private fun tournament(): Tournament {
        val league = leagueRepository.save(
            League(
                leagueName = "LCK",
                logoUrl = "https://example.com/lck.png",
                isActive = true,
                leagueApiId = "lck-api-id",
            ),
        )
        val tournament = tournamentRepository.save(
            Tournament(
                tournamentName = "LCK Summer 2026",
                startDate = LocalDate.now().minusDays(3),
                endDate = LocalDate.now().plusDays(30),
                tournamentApiId = "lck-summer-2026",
                league = league,
            ),
        )
        // matchPollingDao issues raw JDBC via jOOQ, bypassing Hibernate's session — so the
        // tournament row (assigned-TSID entity, insert deferred to flush) must be flushed to
        // the DB now or the DAO's FK-referencing insert fails to see it.
        entityManager.flush()
        return tournament
    }

    private fun match(tournament: Tournament, apiId: String) = Match(
        startTime = ZonedDateTime.now(),
        matchType = MatchType.BO3,
        matchState = MatchState.UNSTARTED,
        matchLabel = "Week 1",
        matchApiId = apiId,
        tournament = tournament,
    )

    private fun clubProfile(abbreviation: String, club: Club?): ClubProfile {
        val profile = clubProfileRepositoryDao.save(
            ClubProfile(
                clubName = abbreviation,
                abbreviation = abbreviation,
                logoUrl = "https://example.com/$abbreviation.png",
                effectiveFrom = LocalDate.now().minusYears(1),
                club = club,
            ),
        )
        // Same flush-before-jOOQ-read requirement as tournament() above.
        entityManager.flush()
        return profile
    }

    @Test
    fun `upsertMatches inserts new matches and returns them with generated ids`() {
        val tournament = tournament()

        val result = matchPollingDao.upsertMatches(listOf(match(tournament, "match-a"), match(tournament, "match-b")))

        assertEquals(2, result.size)
        assertEquals(setOf("match-a", "match-b"), result.map { it.matchApiId }.toSet())
        assertTrue(result.all { it.id != 0L })
        assertTrue(result.all { it.tournament.id == tournament.id })
    }

    @Test
    fun `upsertMatches does not duplicate a match with an already-seen matchApiId`() {
        val tournament = tournament()
        matchPollingDao.upsertMatches(listOf(match(tournament, "match-dup")))
        entityManager.flush()
        entityManager.clear()

        val result = matchPollingDao.upsertMatches(listOf(match(tournament, "match-dup")))
        entityManager.flush()
        entityManager.clear()

        val count = entityManager
            .createQuery("SELECT COUNT(m) FROM Match m WHERE m.matchApiId = :apiId", Long::class.java)
            .setParameter("apiId", "match-dup")
            .singleResult

        assertEquals(1L, count)
        assertEquals(1, result.size)
        assertEquals("match-dup", result.single().matchApiId)
    }

    @Test
    fun `upsertMatches with empty input returns empty list`() {
        assertEquals(emptyList<Match>(), matchPollingDao.upsertMatches(emptyList()))
    }

    @Test
    fun `upsertParticipants does not duplicate a participant with the same match and club profile`() {
        val tournament = tournament()
        val savedMatch = matchPollingDao.upsertMatches(listOf(match(tournament, "match-p"))).single()
        val club = clubRepository.save(Club(isActive = true))
        val profile = clubProfile("T1", club)

        matchPollingDao.upsertParticipants(listOf(MatchParticipant(match = savedMatch, club = club, clubProfile = profile)))
        entityManager.flush()
        entityManager.clear()

        matchPollingDao.upsertParticipants(listOf(MatchParticipant(match = savedMatch, club = club, clubProfile = profile)))
        entityManager.flush()
        entityManager.clear()

        val count = entityManager
            .createQuery(
                "SELECT COUNT(p) FROM MatchParticipant p WHERE p.match.id = :matchId AND p.clubProfile.id = :profileId",
                Long::class.java,
            )
            .setParameter("matchId", savedMatch.id)
            .setParameter("profileId", profile.id)
            .singleResult

        assertEquals(1L, count)
    }

    /**
     * Flush-risk documentation (Task 3): this test method itself is one shared persistence
     * context/EntityManager (this class is @Transactional at the class level), so it
     * reproduces the exact scenario Task 2 found — a client-assigned-TSID JPA entity
     * (ClubProfile) saved via clubProfileRepository.saveAll(...) with its INSERT deferred to
     * flush time, immediately followed by matchPollingDao.upsertParticipants(...) (raw jOOQ
     * JDBC, bypasses the Hibernate session) referencing that profile's FK, with NO explicit
     * entityManager.flush() in between. It genuinely throws a real Postgres FK violation, as
     * asserted below — this documents that the risk is real WHEN the two calls share one
     * persistence context.
     *
     * This is deliberately NOT the same situation as PollMatchDataService in production:
     * PollMatchDataServiceFlushRiskIntegrationTest (in the application.service test package)
     * proves that production's actual call shape — no @Transactional anywhere, each DB call
     * wrapped in withContext(Dispatchers.IO), which dispatches onto a different physical
     * thread with no shared EntityManager/transaction binding visible there — does NOT hit
     * this failure mode, even under a simulated open-in-view request thread. See that test's
     * class-level doc comment for the full reasoning and empirical proof.
     */
    @Test
    fun `upsertParticipants throws a FK violation for a club profile saved via saveAll with no explicit flush in the same persistence context`() {
        val tournament = tournament()
        val savedMatch = matchPollingDao.upsertMatches(listOf(match(tournament, "match-flush-risk"))).single()

        val newProfiles = clubProfileRepositoryDao.saveAll(
            listOf(
                ClubProfile(
                    clubName = "New Team",
                    abbreviation = "NEWT",
                    logoUrl = "https://example.com/NEWT.png",
                    effectiveFrom = LocalDate.now().minusYears(1),
                    club = null,
                ),
            ),
        )
        // Deliberately no entityManager.flush() here — this is the exact gap being tested.

        assertThrows(DataIntegrityViolationException::class.java) {
            matchPollingDao.upsertParticipants(
                listOf(MatchParticipant(match = savedMatch, club = null, clubProfile = newProfiles.single())),
            )
        }
    }

    @Test
    fun `upsertParticipants allows two participants on the same match with different club profiles`() {
        val tournament = tournament()
        val savedMatch = matchPollingDao.upsertMatches(listOf(match(tournament, "match-two-teams"))).single()
        val clubA = clubRepository.save(Club(isActive = true))
        val clubB = clubRepository.save(Club(isActive = true))
        val profileA = clubProfile("T1", clubA)
        val profileB = clubProfile("GEN", clubB)

        matchPollingDao.upsertParticipants(
            listOf(
                MatchParticipant(match = savedMatch, club = clubA, clubProfile = profileA),
                MatchParticipant(match = savedMatch, club = clubB, clubProfile = profileB),
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val count = entityManager
            .createQuery("SELECT COUNT(p) FROM MatchParticipant p WHERE p.match.id = :matchId", Long::class.java)
            .setParameter("matchId", savedMatch.id)
            .singleResult

        assertEquals(2L, count)
    }
}
