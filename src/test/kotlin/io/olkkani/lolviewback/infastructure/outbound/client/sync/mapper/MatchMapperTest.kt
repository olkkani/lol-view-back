package io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper

import io.olkkani.lolviewback.infastructure.outbound.client.sync.dto.MatchApiResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchType
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class MatchMapperTest {

    private fun sampleTournament(): Tournament {
        val league = League(
            leagueName = "LCK",
            logoUrl = "https://example.com/lck.png",
            isActive = true,
            leagueApiId = "league-api-1",
        )
        return Tournament(
            tournamentName = "lck_2025_summer",
            startDate = LocalDate.of(2025, 6, 1),
            endDate = LocalDate.of(2025, 8, 30),
            tournamentApiId = "tournament-api-1",
            league = league,
        )
    }

    @Test
    fun `toEntity maps api response fields onto Match entity using given tournament`() {
        val tournament = sampleTournament()
        val response = MatchApiResponse(
            apiId = "match-api-1",
            startTime = ZonedDateTime.parse("2025-06-01T10:00:00Z"),
            label = "Week 1 Day 1",
            strategyType = "BO3",
            state = "completed",
        )

        val entity = response.toEntity(tournament)

        assertEquals("match-api-1", entity.matchApiId)
        assertEquals(ZonedDateTime.parse("2025-06-01T10:00:00Z"), entity.startTime)
        assertEquals("Week 1 Day 1", entity.matchLabel)
        assertEquals(MatchType.BO3, entity.matchType)
        assertEquals(MatchState.FINISHED, entity.matchState)
        assertEquals(tournament, entity.tournament)
    }

    @Test
    fun `toEntity maps unstarted api state to SCHEDULED`() {
        val tournament = sampleTournament()
        val response = MatchApiResponse(
            apiId = "match-api-2",
            startTime = ZonedDateTime.parse("2025-06-02T10:00:00Z"),
            label = "Week 1 Day 2",
            strategyType = "BO5",
            state = "unstarted",
        )

        val entity = response.toEntity(tournament)

        assertEquals(MatchState.SCHEDULED, entity.matchState)
    }

    @Test
    fun `toEntity throws on unrecognized api state`() {
        val tournament = sampleTournament()
        val response = MatchApiResponse(
            apiId = "match-api-3",
            startTime = ZonedDateTime.parse("2025-06-03T10:00:00Z"),
            label = "Week 1 Day 3",
            strategyType = "BO3",
            state = "some-unknown-state",
        )

        assertThrows(IllegalArgumentException::class.java) {
            response.toEntity(tournament)
        }
    }
}
