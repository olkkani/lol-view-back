package io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper

import io.olkkani.lolviewback.infastructure.outbound.client.sync.dto.TournamentApiResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TournamentMapperTest {

    @Test
    fun `toEntity maps api response fields onto Tournament entity`() {
        val league = League(
            leagueName = "LCK",
            logoUrl = "https://example.com/lck.png",
            isActive = true,
            leagueApiId = "league-api-1",
        )
        val response = TournamentApiResponse(
            apiId = "tournament-api-1",
            name = "lck_2025_summer",
            startDate = LocalDate.of(2025, 6, 1),
            endDate = LocalDate.of(2025, 8, 30),
        )

        val entity = response.toEntity(league)

        assertEquals("tournament-api-1", entity.tournamentApiId)
        assertEquals("lck_2025_summer", entity.tournamentName)
        assertEquals(LocalDate.of(2025, 6, 1), entity.startDate)
        assertEquals(LocalDate.of(2025, 8, 30), entity.endDate)
        assertEquals(league, entity.league)
    }
}
