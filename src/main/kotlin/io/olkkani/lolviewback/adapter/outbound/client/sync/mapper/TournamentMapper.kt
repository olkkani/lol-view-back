package io.olkkani.lolviewback.adapter.outbound.client.sync.mapper

import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.TournamentApiResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament

fun TournamentApiResponse.toEntity(league: League): Tournament {
    return Tournament(
        tournamentName = this.name,
        startDate = this.startDate,
        endDate = this.endDate,
        tournamentApiId = this.apiId,
        league = league,
    )
}
