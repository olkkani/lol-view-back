package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.inbound.web.dto.ExternalIdRequest
import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueRequest
import io.olkkani.lolviewback.adapter.inbound.web.dto.RecurrenceWindowRequest
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRecurrenceWindowRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

/**
 * Exercises LeagueService.create against a real Postgres instance, because the
 * transactional-rollback behavior (a duplicate external_id must undo the already-saved
 * League row, not leave it orphaned) can't be verified with mocked repositories.
 *
 * NOT_SUPPORTED: @DataJpaTest normally wraps each test in its own rolled-back transaction.
 * Here that would make create()'s @Transactional join the test's ambient transaction, so a
 * constraint violation would abort it and poison every later statement (Postgres refuses
 * further commands once a transaction is aborted) - including the row-count assertion this
 * test needs to run afterwards. Suspending it lets create() run its own real, independent
 * transaction, matching how it behaves in production.
 */
@Testcontainers
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(LeagueServiceIntegrationTest.ServiceUnderTestConfig::class)
class LeagueServiceIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @TestConfiguration
    class ServiceUnderTestConfig {
        @Bean
        fun leagueService(
            leagueRepository: LeagueRepository,
            leagueRecurrenceWindowRepository: LeagueRecurrenceWindowRepository,
            leagueExternalIdRepository: LeagueExternalIdRepository,
        ) = LeagueService(leagueRepository, leagueRecurrenceWindowRepository, leagueExternalIdRepository)
    }

    @Autowired
    lateinit var leagueService: LeagueService

    @Autowired
    lateinit var leagueRepository: LeagueRepository

    private fun request(externalId: String) =
        LeagueRequest(
            leagueName = "LCK Challengers",
            logoUrl = "url",
            logoBackdrop = null,
            recurrenceWindows =
                listOf(
                    RecurrenceWindowRequest(
                        label = "Spring",
                        tieBreakPriority = 1,
                        intervalYear = 1,
                        startDate = LocalDate.of(2026, 1, 1),
                    ),
                ),
            externalIds = listOf(ExternalIdRequest(provider = Provider.LOLESPORTS, externalId = externalId)),
        )

    @Test
    fun `create persists the league, its recurrence windows, and its external ids together`() {
        val result = leagueService.create(request("unique-${System.nanoTime()}"))

        val persisted = leagueRepository.findById(result.id)
        assertEquals(true, persisted.isPresent)
        assertEquals(1, result.recurrenceWindows.size)
        assertEquals(1, result.externalIds.size)
    }

    @Test
    fun `create rolls back the whole league when two external ids collide on the same provider`() {
        // The unique constraint is on (entity_id, provider): both external ids below get the new
        // league's entity id, so the second save violates the constraint and must roll back the
        // already-saved League row along with the first external id and the recurrence window.
        val leagueName = "LCK Challengers Rollback ${System.nanoTime()}"

        assertThrows<DataIntegrityViolationException> {
            leagueService.create(
                request("first").copy(
                    leagueName = leagueName,
                    externalIds =
                        listOf(
                            ExternalIdRequest(provider = Provider.LOLESPORTS, externalId = "first"),
                            ExternalIdRequest(provider = Provider.LOLESPORTS, externalId = "second"),
                        ),
                ),
            )
        }

        assertEquals(emptyList<Long>(), leagueRepository.findAll().filter { it.leagueName == leagueName }.map { it.id })
    }

    @Test
    fun `getById reflects a league's persisted recurrence windows and external ids`() {
        val created = leagueService.create(request("unique-${System.nanoTime()}"))

        val fetched = leagueService.getById(created.id)

        assertEquals(1, fetched.recurrenceWindows.size)
        assertEquals("Spring", fetched.recurrenceWindows[0].label)
        assertEquals(1, fetched.externalIds.size)
        assertFalse(fetched.externalIds[0].externalId.isBlank())
    }
}
