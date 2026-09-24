package io.olkkani.lolviewback.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueRequest
import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LogoBackdrop
import io.olkkani.lolviewback.application.service.LeagueService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LeagueRestControllerTest {
    private val leagueService = mockk<LeagueService>()
    private val controller = LeagueRestController(leagueService)

    private fun sampleResponse(id: Long = 1L) =
        LeagueResponse(
            id = id,
            leagueName = "LCK",
            logoUrl = "url",
            isActive = true,
            logoBackdrop = LogoBackdrop.DARK,
            recurrenceWindows = emptyList(),
            externalIds = emptyList(),
        )

    @Test
    fun `creates a league via the service`() {
        val request = LeagueRequest(leagueName = "LCK", logoUrl = "url", logoBackdrop = LogoBackdrop.DARK)
        every { leagueService.create(request) } returns sampleResponse()

        val result = controller.createLeague(request)

        assertEquals(1L, result.id)
        verify { leagueService.create(request) }
    }

    @Test
    fun `returns all leagues via the service`() {
        every { leagueService.getAll() } returns listOf(sampleResponse())

        val result = controller.getLeagues()

        assertEquals(1, result.size)
    }

    @Test
    fun `returns a single league by id via the service`() {
        every { leagueService.getById(1L) } returns sampleResponse()

        val result = controller.getLeague(1L)

        assertEquals(1L, result.id)
    }

    @Test
    fun `updates a league via the service`() {
        val request = LeagueRequest(leagueName = "LCK Updated", logoUrl = "url2", logoBackdrop = null)
        every { leagueService.update(1L, request) } returns sampleResponse().copy(leagueName = "LCK Updated")

        val result = controller.updateLeague(1L, request)

        assertEquals("LCK Updated", result.leagueName)
        verify { leagueService.update(1L, request) }
    }

    @Test
    fun `deletes a league via the service`() {
        every { leagueService.delete(1L) } returns Unit

        controller.deleteLeague(1L)

        verify { leagueService.delete(1L) }
    }
}
