package io.olkkani.lolviewback.adapter.inbound.web.dto

data class BracketMatchResponse(
    val matchId: Long,
    val name: String,
    val status: String,
    val winnerId: Long?,
    val winnerType: String?,
    val previousMatches: String,
    val opponents: String,
    val results: String,
)
