package io.olkkani.lolviewback.adapter.inbound.web

import io.olkkani.lolviewback.adapter.inbound.web.dto.BracketMatchResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.BracketMatchDao
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/tournaments")
class BracketRestController(
    private val bracketMatchDao: BracketMatchDao,
) {
    @GetMapping("/{tournamentId}/brackets")
    fun getBrackets(
        @PathVariable tournamentId: String,
    ): List<BracketMatchResponse> =
        bracketMatchDao.findByTournamentId(tournamentId).map {
            BracketMatchResponse(
                matchId = it.matchId,
                name = it.name,
                status = it.status,
                winnerId = it.winnerId,
                winnerType = it.winnerType,
                previousMatches = it.previousMatches,
                opponents = it.opponents,
                results = it.results,
            )
        }
}
