package io.olkkani.lolviewback.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.olkkani.lolviewback.adapter.inbound.web.dto.ExternalIdRequest
import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueNotFoundException
import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueRequest
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueExternalId
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LogoBackdrop
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

class LeagueServiceTest {
    private val leagueRepository = mockk<LeagueRepository>()
    private val leagueExternalIdRepository = mockk<LeagueExternalIdRepository>()
    private val service = LeagueService(leagueRepository, leagueExternalIdRepository)

    @Test
    fun `creates a league with the given fields and isActive defaulted to true`() {
        val savedSlot = slot<League>()
        every { leagueRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val result =
            service.create(
                LeagueRequest(leagueName = "LCK", logoUrl = "https://example.com/lck.png", logoBackdrop = LogoBackdrop.DARK),
            )

        assertEquals("LCK", result.leagueName)
        assertEquals("https://example.com/lck.png", result.logoUrl)
        assertEquals(LogoBackdrop.DARK, result.logoBackdrop)
        assertTrue(result.isActive)
        assertTrue(savedSlot.captured.isActive)
        assertTrue(result.externalIds.isEmpty())
    }

    @Test
    fun `creates a league with external ids linked to the new league`() {
        val leagueSlot = slot<League>()
        every { leagueRepository.save(capture(leagueSlot)) } answers { leagueSlot.captured.also { it.id = 42L } }
        val externalIdSlot = slot<LeagueExternalId>()
        every { leagueExternalIdRepository.saveAndFlush(capture(externalIdSlot)) } answers { externalIdSlot.captured }

        val result =
            service.create(
                LeagueRequest(
                    leagueName = "LCK",
                    logoUrl = "url",
                    logoBackdrop = null,
                    externalIds = listOf(ExternalIdRequest(provider = Provider.LOLESPORTS, externalId = "98767991310872058")),
                ),
            )

        assertEquals(1, result.externalIds.size)
        assertEquals("98767991310872058", result.externalIds[0].externalId)
        assertEquals(42L, externalIdSlot.captured.entityId)
    }

    @Test
    fun `returns all leagues with their external ids`() {
        val league = League(id = 1L, leagueName = "LCK", logoUrl = "url", isActive = true)
        every { leagueRepository.findAll() } returns listOf(league)
        every { leagueExternalIdRepository.findByEntityIdIn(listOf(1L)) } returns
            listOf(LeagueExternalId(entityId = 1L, provider = Provider.LOLESPORTS, externalId = "abc"))

        val result = service.getAll()

        assertEquals(1, result.size)
        assertEquals("LCK", result[0].leagueName)
        assertEquals(1, result[0].externalIds.size)
    }

    @Test
    fun `returns a league by id with its external ids`() {
        val league = League(id = 1L, leagueName = "LCK", logoUrl = "url", isActive = true)
        every { leagueRepository.findById(1L) } returns Optional.of(league)
        every { leagueExternalIdRepository.findByEntityId(1L) } returns
            listOf(LeagueExternalId(entityId = 1L, provider = Provider.PANDASCORE, externalId = "xyz"))

        val result = service.getById(1L)

        assertEquals(1L, result.id)
        assertEquals(1, result.externalIds.size)
        assertEquals(Provider.PANDASCORE, result.externalIds[0].provider)
    }

    @Test
    fun `throws LeagueNotFoundException when getting a missing league`() {
        every { leagueRepository.findById(99L) } returns Optional.empty()

        assertThrows(LeagueNotFoundException::class.java) { service.getById(99L) }
    }

    @Test
    fun `updates an existing league's editable fields`() {
        val existing = League(id = 1L, leagueName = "Old", logoUrl = "old-url", isActive = true, logoBackdrop = null)
        every { leagueRepository.findById(1L) } returns Optional.of(existing)
        every { leagueRepository.save(any()) } answers { firstArg() }
        every { leagueExternalIdRepository.findByEntityId(1L) } returns emptyList()

        val result =
            service.update(
                1L,
                LeagueRequest(leagueName = "LCK", logoUrl = "new-url", logoBackdrop = LogoBackdrop.LIGHT),
            )

        assertEquals("LCK", result.leagueName)
        assertEquals("new-url", result.logoUrl)
        assertEquals(LogoBackdrop.LIGHT, result.logoBackdrop)
    }

    @Test
    fun `throws LeagueNotFoundException when updating a missing league`() {
        every { leagueRepository.findById(99L) } returns Optional.empty()

        assertThrows(LeagueNotFoundException::class.java) {
            service.update(99L, LeagueRequest(leagueName = "LCK", logoUrl = "url", logoBackdrop = null))
        }
    }

    @Test
    fun `soft deletes a league by setting isActive to false`() {
        val existing = League(id = 1L, leagueName = "LCK", logoUrl = "url", isActive = true)
        every { leagueRepository.findById(1L) } returns Optional.of(existing)
        val savedSlot = slot<League>()
        every { leagueRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        service.delete(1L)

        assertTrue(!savedSlot.captured.isActive)
        verify { leagueRepository.save(any()) }
    }

    @Test
    fun `throws LeagueNotFoundException when deleting a missing league`() {
        every { leagueRepository.findById(99L) } returns Optional.empty()

        assertThrows(LeagueNotFoundException::class.java) { service.delete(99L) }
    }
}
