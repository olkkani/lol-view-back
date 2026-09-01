package io.olkkani.lolviewback.application.outbound

import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchApiResponse
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchSetApiResponse
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.TournamentApiResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament

interface LolApiClientPort {
    fun fetchTournaments(leagueId: String): List<TournamentApiResponse>
    fun fetchMatches(leagueId: String): List<MatchApiResponse>
    fun fetchMatchSetData(matchId: String): MatchSetApiResponse
}