package io.olkkani.lolviewback.adapter.inbound.web

import io.olkkani.lolviewback.adapter.inbound.web.dto.HeadToHeadResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchResponse
import io.olkkani.lolviewback.application.service.MatchQueryService
import io.olkkani.lolviewback.application.service.PollMatchDataService
import io.olkkani.lolviewback.application.service.PollMatchSetService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/matches")
class MatchRestController(
    private val matchQueryService: MatchQueryService,
    private val pollMatchDataService: PollMatchDataService,
) {
    @GetMapping
    fun getMatches(
        @RequestParam range: String,
    ): List<MatchResponse> = matchQueryService.findMatches(MatchRange.from(range))

    @GetMapping("/{matchId}/head-to-head")
    fun getHeadToHead(
        @PathVariable matchId: Long,
    ): List<HeadToHeadResponse> = matchQueryService.findHeadToHead(matchId)

    @GetMapping("/test")
    suspend fun test() {
        pollMatchDataService.syncUpcomingMatches()
    }
}
