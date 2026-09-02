package io.olkkani.lolviewback.adapter.inbound.web.dto

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Club
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubProfile
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LogoBackdrop
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchParticipant
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MatchResponseTest {

    private val kst = ZoneId.of("Asia/Seoul")

    @Test
    fun `toResponse includes the match's league name`() {
        val league = League(
            id = 1L,
            leagueName = "LCK",
            logoUrl = "https://example.com/lck.png",
            isActive = true,
            leagueApiId = "lck-api-id",
        )
        val tournament = Tournament(
            id = 1L,
            tournamentName = "LCK Summer 2026",
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 8, 31),
            tournamentApiId = "lck-summer-2026",
            league = league,
        )
        val match = Match(
            id = 1L,
            startTime = ZonedDateTime.of(2026, 8, 12, 18, 0, 0, 0, kst),
            matchType = MatchType.BO3,
            matchState = MatchState.UNSTARTED,
            matchLabel = "W1",
            matchApiId = "m1",
            tournament = tournament,
        )

        val result = match.toResponse(emptyList(), emptyMap())

        assertEquals("LCK", result.leagueName)
    }

    @Test
    fun `toResponse carries the club profile's logoBackdrop through`() {
        val match = matchWithParticipant(logoBackdrop = LogoBackdrop.DARK)

        val result = match.first.toResponse(listOf(match.second), mapOf(1L to 1))

        assertEquals(LogoBackdrop.DARK, result.clubs[0].logoBackdrop)
    }

    @Test
    fun `toResponse carries a null logoBackdrop through unchanged`() {
        val match = matchWithParticipant(logoBackdrop = null)

        val result = match.first.toResponse(listOf(match.second), mapOf(1L to 1))

        assertNull(result.clubs[0].logoBackdrop)
    }

    private fun matchWithParticipant(logoBackdrop: LogoBackdrop?): Pair<Match, MatchParticipant> {
        val league = League(
            id = 1L,
            leagueName = "LCK",
            logoUrl = "https://example.com/lck.png",
            isActive = true,
            leagueApiId = "lck-api-id",
        )
        val tournament = Tournament(
            id = 1L,
            tournamentName = "LCK Summer 2026",
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 8, 31),
            tournamentApiId = "lck-summer-2026",
            league = league,
        )
        val match = Match(
            id = 1L,
            startTime = ZonedDateTime.of(2026, 8, 12, 18, 0, 0, 0, kst),
            matchType = MatchType.BO3,
            matchState = MatchState.UNSTARTED,
            matchLabel = "W1",
            matchApiId = "m1",
            tournament = tournament,
        )
        val clubProfile = ClubProfile(
            id = 1L,
            clubName = "T1",
            abbreviation = "T1",
            logoUrl = "https://example.com/t1.png",
            effectiveFrom = LocalDate.of(2020, 1, 1),
            effectiveTo = LocalDate.of(2099, 1, 1),
            logoBackdrop = logoBackdrop,
        )
        val participant = MatchParticipant(
            id = 1L,
            match = match,
            club = Club(id = 1L, isActive = true),
            clubProfile = clubProfile,
        )
        return match to participant
    }
}
