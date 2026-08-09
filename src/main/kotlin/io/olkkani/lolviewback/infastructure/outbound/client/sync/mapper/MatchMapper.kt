package io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper

import io.olkkani.lolviewback.infastructure.outbound.client.sync.dto.MatchApiResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchType
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament

fun MatchApiResponse.toEntity(tournament: Tournament): Match {
    return Match(
        startTime = this.startTime,
        matchType = MatchType.valueOf(this.strategyType),
        matchState = mapState(this.state),
        matchLabel = this.label,
        matchApiId = this.apiId,
        tournament = tournament,
    )
}

private fun mapState(apiState: String): MatchState = when (apiState.lowercase()) {
    "unstarted" -> MatchState.SCHEDULED
    "inprogress" -> MatchState.ONGOING
    "completed" -> MatchState.FINISHED
    else -> throw IllegalArgumentException("Unknown match state from API: $apiState")
}
