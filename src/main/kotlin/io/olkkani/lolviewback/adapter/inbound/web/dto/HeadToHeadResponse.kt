package io.olkkani.lolviewback.adapter.inbound.web.dto

data class HeadToHeadResponse(
    val matchId: Long,
    val winnerClubId: Long,
)

/**
 * Thrown when the requested matchId does not exist. Distinct from a bare
 * NoSuchElementException so the controller's 404 handler cannot swallow
 * unrelated internal errors.
 */
class MatchNotFoundException(message: String) : NoSuchElementException(message)

/**
 * Thrown for data-integrity problems that would otherwise silently produce
 * wrong head-to-head results: the same club matched against itself, or a
 * match without exactly 2 club-linked participants.
 */
class InvalidHeadToHeadRequestException(message: String) : IllegalArgumentException(message)
