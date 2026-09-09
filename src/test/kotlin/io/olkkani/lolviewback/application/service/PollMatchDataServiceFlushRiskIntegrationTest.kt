package io.olkkani.lolviewback.application.service

import io.mockk.coEvery
import io.mockk.mockk
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEvent
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEventMatch
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEventStrategy
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEventTeam
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchParticipantRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.application.outbound.LolApiClientPort
import jakarta.persistence.EntityManagerFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.context.request.ServletWebRequest
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Task 3 flush-risk verification.
 *
 * Task 2 found that MatchPollingDao's raw jOOQ JDBC insert cannot see a still-unflushed
 * JPA insert of a client-assigned-TSID entity (Tournament/ClubProfile/Club — none use
 * @GeneratedValue, so Hibernate defers their INSERT to flush time) when both run inside
 * the SAME persistence context/EntityManager. The sibling test in MatchPollingDaoTest
 * ("upsertParticipants sees a club profile saved via saveAll with no explicit flush in
 * between") reproduces that failure directly: within one @Transactional test method (one
 * shared EntityManager), clubProfileRepository.saveAll(...) followed immediately by
 * matchPollingDao.upsertParticipants(...) throws a real FK violation.
 *
 * The open question for THIS test: does PollMatchDataService's actual call shape in
 * production ever hit that same shared-persistence-context situation? It has no
 * @Transactional anywhere, and every DB call inside it is wrapped in
 * withContext(Dispatchers.IO) — which dispatches onto a different physical thread from
 * Kotlin's IO dispatcher pool.
 *
 * There are two production call paths:
 *  - MatchDailySyncScheduler: no HTTP request thread at all, so there is no candidate for
 *    a shared EntityManager in the first place. Each JpaRepository call opens and commits
 *    its own short-lived transaction. Safe by construction.
 *  - MatchRestController.test() (GET /matches/test): a real HTTP request thread. Spring
 *    Boot's spring.jpa.open-in-view defaults to true (not overridden anywhere in this
 *    repo's application.yaml), so in production OpenEntityManagerInViewFilter binds ONE
 *    EntityManager to that request thread for the whole request via
 *    TransactionSynchronizationManager, which is backed by a plain ThreadLocal.
 *
 * This test targets exactly that second path, reproducing the OSIV thread-binding
 * directly with OpenEntityManagerInViewInterceptor (same binding mechanism the servlet
 * Filter uses) instead of going through the full HTTP + Spring Security filter chain
 * (which needs unrelated OAuth2/JWT setup this test should not have to depend on). The
 * interceptor binds an EntityManager to *this* JVM thread; then
 * pollMatchDataService.syncUpcomingMatches() runs from that same thread, with its
 * internal withContext(Dispatchers.IO) calls dispatching onto Kotlin's IO thread pool —
 * physically different threads from the one OSIV bound to.
 *
 * Because this codebase has no coroutine ThreadContextElement bridging
 * TransactionSynchronizationManager's ThreadLocal across dispatch (verified: no
 * kotlinx-coroutines-slf4j/MDC-style propagation, no ThreadContextElement usage anywhere
 * in src/main), the OSIV-bound EntityManager on the original thread is invisible on the
 * Dispatchers.IO worker thread. So each withContext(Dispatchers.IO) { clubProfileRepository
 * .saveAll(...) } / { matchPollingDao.upsertParticipants(...) } call opens its own
 * separate, short-lived transaction on its own IO thread — same as if OSIV were not
 * active at all.
 *
 * This test proves that empirically: scenario forces a brand-new ClubProfile via
 * clubProfileRepository.saveAll(...), then in the very next DB call references that
 * profile's FK from matchPollingDao.upsertParticipants(...) — all while an OSIV
 * EntityManager is bound to the calling thread and NO test-side flush is added anywhere
 * (matching production, which has none). If OSIV plus coroutine dispatch caused the two
 * calls to actually share a persistence context, this would fail with the same FK
 * violation MatchPollingDaoTest reproduces. It does not: the call succeeds and the
 * participant row is visible via the repositories afterward — confirming production is
 * safe as-is under this call pattern, and no EntityManager/flush needs to be added to
 * PollMatchDataService.
 */
@Testcontainers
@SpringBootTest
@Import(PollMatchDataServiceFlushRiskIntegrationTest.MockApiClientConfig::class)
class PollMatchDataServiceFlushRiskIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @TestConfiguration
    class MockApiClientConfig {
        @Bean
        @Primary
        fun lolApiClientPort(): LolApiClientPort = mockk()
    }

    @Autowired
    lateinit var leagueRepository: LeagueRepository

    @Autowired
    lateinit var tournamentRepository: TournamentRepository

    @Autowired
    lateinit var matchRepository: MatchRepository

    @Autowired
    lateinit var matchParticipantRepository: MatchParticipantRepository

    @Autowired
    lateinit var apiClientPort: LolApiClientPort

    @Autowired
    lateinit var pollMatchDataService: PollMatchDataService

    @Autowired
    lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `syncUpcomingMatches under a simulated open-in-view request thread creates a new club profile and its participant, with no service-side flush`() {
        val league = leagueRepository.save(
            League(
                leagueName = "LCK",
                logoUrl = "https://example.com/lck.png",
                isActive = true,
                leagueApiId = "flush-risk-league",
            ),
        )
        tournamentRepository.save(
            Tournament(
                tournamentName = "LCK Flush Risk Cup",
                startDate = LocalDate.now().minusDays(1),
                endDate = LocalDate.now().plusDays(1),
                tournamentApiId = "flush-risk-tournament",
                league = league,
            ),
        )
        // No explicit flush: tournamentRepository.save(...) is its own short-lived
        // Spring Data JPA transaction, which commits (and thus flushes) before this line
        // returns, exactly like every other individual JpaRepository call in production.

        val event = MatchScheduleEvent(
            startTime = ZonedDateTime.now(),
            state = "unstarted",
            blockName = "Week 1",
            match = MatchScheduleEventMatch(
                id = "flush-risk-match",
                teams = listOf(
                    MatchScheduleEventTeam(name = "Brand New Team", code = "BNT", image = "https://example.com/bnt.png"),
                ),
                strategy = MatchScheduleEventStrategy(type = "bestOf", count = 3),
            ),
        )
        // The persistence-local profile loads db/seed fixtures (V9.2__seed-matchs.sql),
        // which can seed its own in-progress tournaments/leagues. Stub every league id
        // with an empty result by default so findInProgressTournaments() picking up a
        // seeded tournament besides this test's own doesn't blow up on an unstubbed call,
        // then override just this test's league id with the real scenario event.
        coEvery { apiClientPort.fetchMatches(any()) } returns emptyList()
        coEvery { apiClientPort.fetchMatches("flush-risk-league") } returns listOf(event)

        // Simulate spring.jpa.open-in-view=true's effect: bind one EntityManager to THIS
        // thread for the duration of the call, exactly as OpenEntityManagerInViewFilter
        // does for the servlet request thread in a real GET /matches/test call.
        val osivInterceptor = OpenEntityManagerInViewInterceptor()
        osivInterceptor.setEntityManagerFactory(this.entityManagerFactory)
        val mockRequest = MockHttpServletRequest()
        val requestAttributes = ServletRequestAttributes(mockRequest)
        val webRequest = ServletWebRequest(mockRequest)
        RequestContextHolder.setRequestAttributes(requestAttributes)
        try {
            osivInterceptor.preHandle(webRequest)
            try {
                runBlocking {
                    pollMatchDataService.syncUpcomingMatches()
                }
            } finally {
                osivInterceptor.afterCompletion(webRequest, null)
            }
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }

        val savedMatch = matchRepository.findByMatchApiId("flush-risk-match")
        assertNotNull(savedMatch)
        assertEquals(MatchState.UNSTARTED, savedMatch!!.matchState)

        val participants = matchParticipantRepository.findByMatchIdIn(listOf(savedMatch.id))
        assertEquals(1, participants.size)
        assertEquals("BNT", participants.single().clubProfile.abbreviation)
    }
}
