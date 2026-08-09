package io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper

import io.olkkani.lolviewback.infastructure.outbound.client.sync.dto.TournamentApiResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament

fun TournamentApiResponse.toEntity(league: League): Tournament {
    return Tournament(
        tournamentName = this.name,
        startDate = this.startDate,
        endDate = this.endDate,
        tournamentApiId = this.apiId,
        league = league,
    )
}
