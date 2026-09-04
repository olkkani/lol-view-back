package io.olkkani.lolviewback.application.outbound

import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchApiResponse
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEvent
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchSetApiResponse
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.TournamentApiResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament

interface LolApiClientPort {
    suspend fun fetchTournaments(leagueApiId: String): List<TournamentApiResponse>
    suspend fun fetchMatchesForLeague(leagueApiId: String): List<MatchApiResponse>
    suspend fun fetchMatchDetail(matchApiId: String, leagueApiId: String): MatchApiResponse?
    suspend fun fetchMatches(leagueApiId: String): List<MatchScheduleEvent>
    suspend fun fetchMatchSet(matchApiId: String): MatchSetApiResponse?
}