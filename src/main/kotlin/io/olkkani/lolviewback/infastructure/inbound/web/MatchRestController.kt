package io.olkkani.lolviewback.infastructure.inbound.web

import io.olkkani.lolviewback.domain.match.MatchQueryService
import io.olkkani.lolviewback.infastructure.inbound.web.dto.InvalidMatchRangeException
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class MatchRestController(
    private val matchQueryService: MatchQueryService,
) {

    @GetMapping("/matches")
    fun getMatches(@RequestParam range: String): List<MatchResponse> {
        return matchQueryService.findMatches(MatchRange.from(range))
    }

    @ExceptionHandler(InvalidMatchRangeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidRange(ex: InvalidMatchRangeException): Map<String, String> {
        return mapOf("error" to (ex.message ?: "Invalid request"))
    }
}
