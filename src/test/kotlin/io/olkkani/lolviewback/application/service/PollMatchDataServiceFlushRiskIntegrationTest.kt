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
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.transaction.support.TransactionSynchronizationManager
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
 * the SAME persistence context/EntityManager while an outer transaction is left open
 * across both calls (Task 2's Testcontainers test used one @Transactional test method
 * wrapping both the JPA save and the jOOQ read). The sibling test in MatchPollingDaoTest
 * ("upsertParticipants throws a FK violation for a club profile saved via saveAll with no
 * explicit flush in the same persistence context") reproduces that failure directly:
 * within one @Transactional test method (one shared EntityManager, one still-open outer
 * transaction spanning both calls), clubProfileRepository.saveAll(...) followed
 * immediately by matchPollingDao.upsertParticipants(...) throws a real FK violation.
 * That test is also this test's negative-control evidence that the failure mode itself
 * is real and observable in this codebase when an outer transaction actually spans both
 * calls — see "Fix round 1" in task-3-report.md for the harness-level red-step
 * confirmation specific to THIS test, including a correction to the mechanism described
 * below.
 *
 * The open question for THIS test: does PollMatchDataService's actual call shape in
 * production ever hit that same situation? It has no @Transactional anywhere, and every
 * DB call inside it is wrapped in withContext(Dispatchers.IO), which dispatches onto a
 * different physical thread from Kotlin's IO dispatcher pool.
 *
 * There are two production call paths:
 *  - MatchDailySyncScheduler: no HTTP request thread at all, so there is no candidate for
 *    a shared, OSIV-bound EntityManager in the first place. Each JpaRepository call opens
 *    and commits its own short-lived transaction. Safe by construction.
 *  - MatchRestController.test() (GET /matches/test): a real HTTP request, handled by a
 *    `suspend fun` controller method. This app is Spring MVC (starter-web + Jetty, not
 *    WebFlux), and Spring MVC's support for suspend handlers works via Spring's
 *    coroutines-to-DeferredResult adapter, which runs the handler body through Servlet
 *    ASYNC dispatch rather than synchronously to completion on the original request
 *    thread. Spring Boot's spring.jpa.open-in-view defaults to true (not overridden
 *    anywhere in this repo's application.yaml), so OpenEntityManagerInViewFilter/
 *    OpenEntityManagerInViewInterceptor DOES bind an EntityManager for the request via
 *    TransactionSynchronizationManager (a plain ThreadLocal) — but exactly which
 *    thread(s) the suspend handler body, and therefore this binding, is visible from is
 *    governed by Spring MVC's async-dispatch machinery, not a simple "one thread for the
 *    whole request" model.
 *
 * This test does NOT attempt to precisely reproduce that async-dispatch mechanics —
 * doing so would require a live Servlet container round trip. Instead it deliberately
 * models a WORST-CASE approximation for OSIV *binding visibility*: OpenEntityManagerInView
 * Interceptor binds an EntityManager directly to *this* JUnit thread — the same thread
 * that then calls runBlocking { pollMatchDataService.syncUpcomingMatches() } — so
 * whatever thread-hopping Spring MVC's real async dispatch does in production, the
 * OSIV-bound EntityManager is at least as reachable here as it would ever be in
 * production (arguably more so, since this test keeps it on the very thread the coroutine
 * launches from). A pass here is therefore not weaker evidence than a precise MVC
 * reproduction would be.
 *
 * IMPORTANT CORRECTION (Fix round 1): the original version of this comment claimed the
 * safety mechanism was that withContext(Dispatchers.IO)'s thread-hop makes the
 * OSIV-bound EntityManager (bound via a plain ThreadLocal, with no coroutine
 * ThreadContextElement bridging it across dispatch) invisible to the DB calls. A
 * red-step check falsified that specific claim: temporarily replacing every
 * withContext(Dispatchers.IO) in PollMatchDataService with
 * withContext(EmptyCoroutineContext) (which does NOT introduce a thread-hop — confirmed
 * by logging Thread.currentThread().name and
 * TransactionSynchronizationManager.getResourceMap() at each DB call site, which showed
 * the same thread and the OSIV-bound EntityManager visible at all three call sites) and
 * re-running this test still produced a PASS, not the expected FK violation. So the
 * thread-hop is not what makes this safe.
 *
 * The actual mechanism: OpenEntityManagerInViewInterceptor.preHandle binds an
 * EntityManager as a resource but does NOT open an ambient transaction on it. Each
 * individual Spring Data JPA repository method (SimpleJpaRepository.saveAll,
 * findByAbbreviationIn, etc.) is itself @Transactional with REQUIRED propagation, so
 * calling it — on any thread, whether or not it reuses an OSIV-bound EntityManager —
 * opens its own self-contained transaction and commits (thus flushes) it before the
 * method returns, because PollMatchDataService never wraps multiple repository calls in
 * one outer @Transactional that would keep that commit from happening. Task 2's failure
 * only reproduces when an outer @Transactional test boundary keeps the transaction open
 * across BOTH the JPA save and the jOOQ read; PollMatchDataService, in every production
 * call path, never does that — each write commits before the next call starts, sharing
 * an EntityManager or not, same thread or not. This is a stronger safety property than
 * "different thread" would have been: it holds regardless of any future change to
 * withContext's dispatcher choice, as long as no one wraps these calls in a shared
 * @Transactional.
 *
 * This test proves the practical case empirically: scenario forces a brand-new
 * ClubProfile via clubProfileRepository.saveAll(...), then in the very next DB call
 * references that profile's FK from matchPollingDao.upsertParticipants(...) — all while
 * an OSIV EntityManager is bound to the calling thread (asserted explicitly right after
 * preHandle, below, as a negative-control check on the harness itself) and NO test-side
 * flush is added anywhere (matching production, which has none). It succeeds: the call
 * completes and the participant row is visible via the repositories afterward —
 * confirming production is safe as-is under this call pattern, and no EntityManager/flush
 * needs to be added to PollMatchDataService.
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
            // Negative-control sanity check on the harness itself: confirm the EntityManager
            // binding actually took before trusting the "no flush needed" result below. If
            // this assertion ever failed, the OSIV simulation silently no-op'd and the rest
            // of this test would be passing for the wrong reason (nothing bound == nothing to
            // leak). See the class doc comment's "Fix round 1" note for the red-step
            // confirmation that this harness does go red when it should.
            assertTrue(
                TransactionSynchronizationManager.hasResource(entityManagerFactory),
                "OpenEntityManagerInViewInterceptor.preHandle did not bind an EntityManager " +
                    "to this thread — the harness is not actually simulating open-in-view.",
            )
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
