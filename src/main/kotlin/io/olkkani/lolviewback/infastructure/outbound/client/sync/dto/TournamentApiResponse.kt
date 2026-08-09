package io.olkkani.lolviewback.infastructure.outbound.client.sync.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

/**
 * Top-level response wrapper for esports-api's `getTournamentsForLeague` endpoint.
 *
 * Real captured shape (see docs/samples/tournament-response-sample.json):
 * ```
 * {
 *   "data": {
 *     "leagues": [
 *       { "tournaments": [ { "id": "...", "slug": "...", "startDate": "...", "endDate": "..." }, ... ] }
 *     ]
 *   }
 * }
 * ```
 *
 * Note: the "leagues" array observed in practice contains a single element for a
 * single-leagueId request, and that element carries no fields of its own besides
 * "tournaments" (no echoed league id/name).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TournamentApiResponseWrapper(
    @JsonProperty("data")
    val data: TournamentApiResponseData,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TournamentApiResponseData(
    @JsonProperty("leagues")
    val leagues: List<TournamentApiResponseLeague>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TournamentApiResponseLeague(
    @JsonProperty("tournaments")
    val tournaments: List<TournamentApiResponse>,
)

/**
 * Per-tournament shape consumed by Task 4's mapper.
 *
 * CONCERN: the real API does not return a tournament "name" field — only "id",
 * "slug" (e.g. "lec_split_3_2026"), "startDate", and "endDate". There is no
 * human-readable display name anywhere in this payload. [name] is mapped from
 * "slug" as the closest available analog; a later task/reviewer should decide
 * whether "slug" is an acceptable substitute for a display name or whether a
 * separate source (e.g. the getLeagues endpoint, or a formatted/derived name)
 * is needed instead.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TournamentApiResponse(
    @JsonProperty("id")
    val apiId: String,
    @JsonProperty("slug")
    val name: String,
    @JsonProperty("startDate")
    val startDate: LocalDate,
    @JsonProperty("endDate")
    val endDate: LocalDate,
)
