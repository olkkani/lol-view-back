package io.olkkani.lolviewback.adapter.outbound.client.sync.dto

import com.fasterxml.jackson.annotation.JsonCreator
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchSetState

data class MatchSetApiResponseWrapper(
    val data: MatchSetEventDataResponse
)

data class MatchSetEventDataResponse(
    val event: MatchSetApiResponse
)

// ── 실제 도메인 계층 구조 (Wrapper 아님, 매치 자체의 depth)
data class MatchSetApiResponse(
    val id: String,
    val type: String,
    val tournament: TournamentResponse,
    val league: LeagueResponse,
    val match: MatchResponse,
    val streams: List<Any>
)

data class TournamentResponse(
    val id: String
)

data class LeagueResponse(
    val id: String,
    val slug: String,
    val image: String,
    val name: String
)

data class MatchResponse(
    val strategy: StrategyResponse,
    val teams: List<TeamResponse>,
    val games: List<GameResponse>
)

data class StrategyResponse(
    val count: Int
)

data class TeamResponse(
    val id: String,
    val name: String,
    val code: String,
    val image: String,
    val result: ResultResponse
)

data class ResultResponse(
    val gameWins: Int
)

data class GameResponse(
    val number: Int,
    val id: String,
    val state: SetState,
    val teams: List<GameTeamResponse>,
    val vods: List<VodResponse>
)

data class GameTeamResponse(
    val id: String,
    val side: String
)

data class VodResponse(
    val id: String,
    val parameter: String,
    val locale: String,
    val mediaLocale: MediaLocaleResponse,
    val provider: String,
    val offset: Int,
    val firstFrameTime: String,
    val startMillis: Long,
    val endMillis: Long
)

data class MediaLocaleResponse(
    val locale: String,
    val englishName: String,
    val translatedName: String
)

enum class SetState {
    UNSTARTED,
    IN_PROGRESS,
    COMPLETED,
    UNNEEDED,
    UNKNOWN;

    companion object {
        private val API_VALUE_MAPPING = mapOf(
            "unstarted" to UNSTARTED,
            "inProgress" to IN_PROGRESS,
            "completed" to COMPLETED,
            "unneeded" to UNNEEDED,
        )

        @JsonCreator
        @JvmStatic
        fun from(apiValue: String): SetState =
            API_VALUE_MAPPING[apiValue.lowercase()] ?: UNKNOWN
    }
}