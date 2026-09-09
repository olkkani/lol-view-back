package io.olkkani.lolviewback.adapter.outbound.persistence.dao

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchParticipant
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import org.jooq.DSLContext
import org.jooq.generated.Tables
import org.springframework.stereotype.Repository
import java.time.ZoneId

@Repository
class MatchPollingDao(
    private val dsl: DSLContext,
) {
    private val matches = Tables.MATCHES
    private val matchParticipants = Tables.MATCH_PARTICIPANTS

    /**
     * Inserts each match, skipping any whose match_api_id already exists
     * (ON CONFLICT DO NOTHING — a repeated poll of an already-saved match is
     * expected, not an error). Returns every Match for the given matchApiIds,
     * newly-inserted or pre-existing, each carrying its persisted id.
     */
    fun upsertMatches(matchesToSave: List<Match>): List<Match> {
        if (matchesToSave.isEmpty()) return emptyList()

        val tournamentById = matchesToSave.associate { it.matchApiId to it.tournament }

        val insert = dsl.insertInto(
            matches,
            matches.ID,
            matches.START_TIME,
            matches.MATCH_TYPE,
            matches.MATCH_STATE,
            matches.MATCH_LABEL,
            matches.MATCH_API_ID,
            matches.TOURNAMENT_ID,
        )
        matchesToSave.forEach { match ->
            insert.values(
                match.id,
                match.startTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime(),
                match.matchType.name,
                match.matchState.name,
                match.matchLabel,
                match.matchApiId,
                match.tournament.id,
            )
        }
        insert
            .onConflict(matches.MATCH_API_ID)
            .doNothing()
            .execute()

        val apiIds = matchesToSave.map { it.matchApiId }
        return dsl.selectFrom(matches)
            .where(matches.MATCH_API_ID.`in`(apiIds))
            .fetch { record ->
                Match(
                    id = record.id!!,
                    startTime = record.startTime!!.atZone(ZoneId.of("UTC")),
                    matchType = MatchType.valueOf(record.matchType!!),
                    matchState = MatchState.valueOf(record.matchState!!),
                    matchLabel = record.matchLabel!!,
                    matchApiId = record.matchApiId!!,
                    tournament = tournamentById.getValue(record.matchApiId!!),
                )
            }
    }

    /**
     * Inserts each participant, skipping any whose (match_id, club_profile_id)
     * already exists. club_profile_id (not club_id) is the dedup key because
     * club_id is nullable and Postgres unique constraints do not deduplicate
     * NULLs against each other.
     */
    fun upsertParticipants(participants: List<MatchParticipant>) {
        if (participants.isEmpty()) return

        val insert = dsl.insertInto(
            matchParticipants,
            matchParticipants.ID,
            matchParticipants.MATCH_ID,
            matchParticipants.CLUB_ID,
            matchParticipants.CLUB_PROFILE_ID,
        )
        participants.forEach { participant ->
            insert.values(participant.id, participant.match.id, participant.club?.id, participant.clubProfile.id)
        }
        insert
            .onConflict(matchParticipants.MATCH_ID, matchParticipants.CLUB_PROFILE_ID)
            .doNothing()
            .execute()
    }
}
