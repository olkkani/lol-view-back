package io.olkkani.lolviewback.adapter.inbound.web

import io.olkkani.lolviewback.adapter.inbound.web.dto.InvalidHeadToHeadRequestException
import io.olkkani.lolviewback.adapter.inbound.web.dto.InvalidMatchRangeException
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Renders RFC 9457 (application/problem+json) bodies for the domain exceptions
 * raised by the web layer. Centralised here so any controller gets the same
 * error contract, rather than each controller declaring its own @ExceptionHandler.
 *
 * Status is carried on the returned ProblemDetail itself (not @ResponseStatus) -
 * that's the field Spring MVC reads to set the HTTP status for this return type.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(InvalidMatchRangeException::class, InvalidHeadToHeadRequestException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request")

    @ExceptionHandler(MatchNotFoundException::class)
    fun handleNotFound(ex: MatchNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found")
}
