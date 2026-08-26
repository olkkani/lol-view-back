package io.olkkani.lolviewback.adapter.inbound.web.dto

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LogoBackdrop
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchParticipant
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import java.time.ZoneId
import java.time.ZonedDateTime

data class MatchResponse(
    val id: Long,
    val startTime: ZonedDateTime,
    val matchState: MatchState,
    val matchLabel: String,
    val leagueName: String,
    val clubs: List<MatchClubResponse>,
)

data class MatchClubResponse(
    val name: String,
    val logoUrl: String,
    val logoBackdrop: LogoBackdrop?,
    val score: Int,
)

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

fun Match.toResponse(participants: List<MatchParticipant>): MatchResponse {
    return MatchResponse(
        id = requireNotNull(this.id),
        startTime = this.startTime.withZoneSameInstant(KST),
        matchState = this.matchState,
        matchLabel = this.matchLabel,
        leagueName = this.tournament.league.leagueName,
        clubs = participants.map { it.toClubResponse() },
    )
}

private fun MatchParticipant.toClubResponse(): MatchClubResponse {
    return MatchClubResponse(
        name = this.clubProfile.abbreviation,
        logoUrl = this.clubProfile.logoUrl,
        logoBackdrop = this.clubProfile.logoBackdrop,
        score = this.score,
    )
}
