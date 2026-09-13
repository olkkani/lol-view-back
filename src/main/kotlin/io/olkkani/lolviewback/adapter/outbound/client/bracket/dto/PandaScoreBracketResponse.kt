package io.olkkani.lolviewback.adapter.outbound.client.bracket.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.JsonNode

/**
 * Response shape for PandaScore's `GET /tournaments/{id}/brackets` endpoint.
 *
 * Real captured shape (verified live against LCK 2026 Playoffs, tournament_id=21722):
 * ```
 * [
 *   {
 *     "id": 1642162,
 *     "name": "Lower bracket round 1: BFX vs DK",
 *     "status": "finished",
 *     "results": [{"team_id": 132531, "score": 3}, {"team_id": 134115, "score": 2}],
 *     "slug": "2026-09-03-572d56d4-...",
 *     "games": [ ... ],
 *     "winner_id": 132531,
 *     "winner_type": "Team",
 *     "tournament_id": 21722,
 *     "scheduled_at": "2026-09-03T08:00:00Z",
 *     "opponents": [
 *       {"type": "Team", "opponent": {"id": 134115, "name": "BNK FEARX", ...}},
 *       {"type": "Team", "opponent": {"id": 132531, "name": "Dplus KIA", ...}}
 *     ],
 *     "previous_matches": [
 *       {"type": "loser", "match_id": 1642153},
 *       {"type": "loser", "match_id": 1642154}
 *     ]
 *   }, ...
 * ]
 * ```
 *
 * The TOP-LEVEL response is a bare JSON array, unlike lolesports's
 * `{"data": {...}}` wrapper shape — there is no [PandaScoreBracketResponse]
 * wrapper class; callers deserialize directly to `List<PandaScoreMatch>`.
 *
 * CONCERN 1 (previous_matches.type): only "winner" and "loser" were observed
 * across all 9 matches in the sample tournament, including the grand final
 * (which had two incoming "winner" links, one terminal match — no
 * bracket-reset scenario was present in this sample). A third value (e.g. a
 * distinct grand-final/decider marker) has NOT been ruled out for other
 * tournament formats; [PandaScoreMatch.previousMatches] is kept as a raw
 * string field (not an enum) specifically because this is unconfirmed.
 *
 * CONCERN 2 (opponents cardinality): `opponents` was always length-2 in this
 * sample (both slots filled — the tournament was already fully played out).
 * PandaScore's own docs describe "TBD vs TBD" matches with fewer or zero
 * populated opponents when a predecessor hasn't finished yet;
 * [PandaScoreMatch.opponents] is stored as raw JSON precisely to avoid
 * assuming a fixed length.
 *
 * CONCERN 3 (no bracket_type field): there is no field anywhere in this
 * response indicating "upper bracket" vs "lower bracket" as a clean enum.
 * The only hints are [PandaScoreMatch.previousMatches] edge types and the
 * free-text [PandaScoreMatch.name] field (e.g. "Upper bracket quarterfinal
 * 1: T1 vs BFX"). Neither is parsed into a structured field here — both are
 * stored raw, and bracket-side inference (if ever needed) happens at read
 * time, not sync time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PandaScoreMatch(
    @JsonProperty("id")
    val id: Long,
    @JsonProperty("name")
    val name: String,
    @JsonProperty("status")
    val status: String,
    @JsonProperty("slug")
    val slug: String?,
    @JsonProperty("winner_id")
    val winnerId: Long?,
    @JsonProperty("winner_type")
    val winnerType: String?,
    @JsonProperty("tournament_id")
    val tournamentId: Long,
    @JsonProperty("begin_at")
    val beginAt: String?,
    @JsonProperty("end_at")
    val endAt: String?,
    @JsonProperty("scheduled_at")
    val scheduledAt: String?,
    @JsonProperty("previous_matches")
    val previousMatches: JsonNode,
    @JsonProperty("opponents")
    val opponents: JsonNode,
    @JsonProperty("results")
    val results: JsonNode,
)
