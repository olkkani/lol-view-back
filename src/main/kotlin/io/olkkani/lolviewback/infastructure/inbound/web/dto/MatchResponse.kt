package io.olkkani.lolviewback.infastructure.inbound.web.dto

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchParticipant
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import java.time.ZoneId
import java.time.ZonedDateTime

data class MatchResponse(
    val id: Long,
    val startTime: ZonedDateTime,
    val matchState: MatchState,
    val matchLabel: String,
    val clubs: List<MatchClubResponse>,
)

data class MatchClubResponse(
    val name: String,
    val logoUrl: String,
    val score: Int,
)

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

fun Match.toResponse(participants: List<MatchParticipant>): MatchResponse {
    return MatchResponse(
        id = requireNotNull(this.id),
        startTime = this.startTime.withZoneSameInstant(KST),
        matchState = this.matchState,
        matchLabel = this.matchLabel,
        clubs = participants.map { it.toClubResponse() },
    )
}

private fun MatchParticipant.toClubResponse(): MatchClubResponse {
    return MatchClubResponse(
        name = this.clubProfile.clubName,
        logoUrl = this.clubProfile.logoUrl,
        score = this.score,
    )
}
