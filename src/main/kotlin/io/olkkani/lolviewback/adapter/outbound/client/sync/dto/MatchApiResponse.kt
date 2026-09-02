package io.olkkani.lolviewback.adapter.outbound.client.sync.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZonedDateTime

/**
 * Top-level response wrapper for esports-api's `getSchedule` endpoint.
 *
 * Real captured shape (see docs/samples/match-response-sample.json):
 * ```
 * {
 *   "data": {
 *     "schedule": {
 *       "pages": { ... },
 *       "events": [
 *         {
 *           "startTime": "2026-04-12T17:15:00Z",
 *           "state": "completed",
 *           "type": "match",
 *           "blockName": "3주 차",
 *           "league": { "name": "LEC", "slug": "lec" },
 *           "match": {
 *             "id": "115548668059523664",
 *             "flags": ["hasVod"],
 *             "teams": [ ... ],
 *             "strategy": { "type": "bestOf", "count": 3 }
 *           }
 *         }, ...
 *       ]
 *     }
 *   }
 * }
 * ```
 *
 * "id" lives at event.match.id, not at the event's top level, so the flattening
 * into [MatchApiResponse] happens via [MatchScheduleEvent.toMatchApiResponse].
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchApiResponseWrapper(
    @JsonProperty("data")
    val data: MatchApiResponseData,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchApiResponseData(
    @JsonProperty("schedule")
    val schedule: MatchApiResponseSchedule,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchApiResponseSchedule(
    @JsonProperty("events")
    val events: List<MatchScheduleEvent>,
)

/**
 * Raw per-event shape as returned by the API. Deliberately kept separate from
 * [MatchApiResponse] (the flattened shape Task 4's mapper expects) because the
 * match id and BO3/BO5 info are nested under "match", not at the event's top level.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchScheduleEvent(
    @JsonProperty("startTime")
    val startTime: ZonedDateTime,
    @JsonProperty("state")
    val state: String,
    @JsonProperty("blockName")
    val blockName: String?,
    @JsonProperty("match")
    val match: MatchScheduleEventMatch,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchScheduleEventMatch(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("teams")
    val teams: List<MatchScheduleEventTeam>,
    @JsonProperty("strategy")
    val strategy: MatchScheduleEventStrategy,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchScheduleEventTeam(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("code")
    val code: String,
    @JsonProperty
    val image: String,

    )


@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchScheduleEventStrategy(
    @JsonProperty("type")
    val type: String,
    @JsonProperty("count")
    val count: Int,
)

/**
 * Per-match shape consumed by Task 4's mapper.
 *
 * CONCERN 1 (label): the real API does not return a match "label" field —
 * neither at the event's top level nor inside "match". The closest available
 * analog is the event's "blockName" (e.g. "3주 차" / "Week 3"), which describes
 * the schedule block/round the match belongs to, not a per-match display name.
 * [label] is mapped from "blockName" as a placeholder; a reviewer should decide
 * whether that is acceptable or whether label should instead be derived (e.g.
 * from team names) downstream.
 *
 * CONCERN 2 (strategyType): there is no flat "strategy" string field. The real
 * shape is `match.strategy = { "type": "bestOf", "count": 3|5 }`. Observed
 * values in the sample were always `type: "bestOf"` with `count` in {3, 5}.
 * [strategyType] is populated as "BO${count}" (e.g. "BO3", "BO5") to match the
 * brief's assumed string shape; Task 4's mapper (or this DTO) should be revisited
 * if a `type` other than "bestOf" is ever observed.
 *
 * CONCERN 3 (state casing): observed "state" values are lowercase
 * ("completed", "unstarted"); no "inProgress"-equivalent value was observed in
 * this sample, but lolesports' public schedule UI is known to also use
 * "inProgress". Task 4's mapper must not assume case-insensitivity is handled
 * automatically — it will need explicit lowercase-to-enum mapping.
 */
data class MatchApiResponse(
    val apiId: String,
    val startTime: ZonedDateTime,
    val label: String,
    val strategyType: String,
    val state: String,
) {
    companion object {
        fun from(event: MatchScheduleEvent): MatchApiResponse =
            MatchApiResponse(
                apiId = event.match.id,
                startTime = event.startTime,
                label = event.blockName ?: "",
                strategyType = "BO${event.match.strategy.count}",
                state = event.state,
            )
    }
}

data class MatchApiTeam(
    val name: String,
    val code: String,
)

data class MatchParticipantApiResponse(
    val id: String,
)


enum class MatchApiState(val value: String) {
    UNSTARTED("unstarted"),
    COMPLETED("completed"),
    IN_PROGRESS("inProgress");

    override fun toString(): String = value
}

//fun MatchScheduleEventTeam.toMatchParticipantEntity() = MatchParticipantApiResponse()