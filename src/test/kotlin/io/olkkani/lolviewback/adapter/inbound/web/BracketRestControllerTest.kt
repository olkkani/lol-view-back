package io.olkkani.lolviewback.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.BracketMatchDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.BracketMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BracketRestControllerTest {

    @Test
    fun `returns raw bracket match rows for a tournament, edges intact for client-side tree assembly`() {
        val dao = mockk<BracketMatchDao>()
        every { dao.findByTournamentId("115548147890329817") } returns
            listOf(
                BracketMatch(
                    id = 1L,
                    tournamentId = "115548147890329817",
                    matchId = 1642162L,
                    name = "Lower bracket round 1: BFX vs DK",
                    status = "finished",
                    slug = "2026-09-03-572d56d4",
                    winnerId = 132531L,
                    winnerType = "Team",
                    beginAt = null,
                    endAt = null,
                    scheduledAt = null,
                    previousMatches = """[{"type":"loser","match_id":1642153}]""",
                    opponents = "[]",
                    results = "[]",
                ),
            )

        val controller = BracketRestController(dao)

        val result = controller.getBrackets("115548147890329817")

        assertEquals(1, result.size)
        assertEquals(1642162L, result[0].matchId)
        assertEquals("finished", result[0].status)
        assertEquals("""[{"type":"loser","match_id":1642153}]""", result[0].previousMatches)
    }
}
