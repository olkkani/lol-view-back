package io.olkkani.lolviewback.adapter.inbound.web

import io.olkkani.lolviewback.adapter.inbound.web.dto.HeadToHeadResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.InvalidHeadToHeadRequestException
import io.olkkani.lolviewback.adapter.inbound.web.dto.InvalidMatchRangeException
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchNotFoundException
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchResponse
import io.olkkani.lolviewback.application.service.MatchQueryService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/matches")
class MatchRestController(
    private val matchQueryService: MatchQueryService,
) {
    @GetMapping
    fun getMatches(
        @RequestParam range: String,
    ): List<MatchResponse> = matchQueryService.findMatches(MatchRange.from(range))

    @GetMapping("/{matchId}/head-to-head")
    fun getHeadToHead(
        @PathVariable matchId: Long,
    ): List<HeadToHeadResponse> = matchQueryService.findHeadToHead(matchId)

    @ExceptionHandler(InvalidMatchRangeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidRange(ex: InvalidMatchRangeException): Map<String, String> = mapOf("error" to (ex.message ?: "Invalid request"))

    @ExceptionHandler(InvalidHeadToHeadRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidHeadToHeadRequest(ex: InvalidHeadToHeadRequestException): Map<String, String> =
        mapOf("error" to (ex.message ?: "Invalid request"))

    @ExceptionHandler(MatchNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleMatchNotFound(ex: MatchNotFoundException): Map<String, String> = mapOf("error" to (ex.message ?: "Not found"))
}
