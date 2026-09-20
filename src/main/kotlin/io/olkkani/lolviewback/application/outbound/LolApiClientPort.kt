package io.olkkani.lolviewback.application.outbound

import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchApiResponse
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEvent
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchSetApiResponse
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.TournamentApiResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider

data class ProviderExternalId(val provider: Provider, val value: String)

interface LolApiClientPort {
    suspend fun fetchTournaments(leagueId: ProviderExternalId): List<TournamentApiResponse>
    suspend fun fetchMatchesForLeague(leagueId: ProviderExternalId): List<MatchApiResponse>
    suspend fun fetchMatchDetail(matchApiId: String, leagueId: ProviderExternalId): MatchApiResponse?
    suspend fun fetchMatches(leagueId: ProviderExternalId): List<MatchScheduleEvent>
    suspend fun fetchMatchSet(matchApiId: String): MatchSetApiResponse?
}
