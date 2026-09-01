package io.olkkani.lolviewback.adapter.outbound.persistence.projection

data class MatchSetProjection(
    val matchId: Long,
    val clubId: Long,
    val abbreviation: String?,
    val wins: Int,
)
