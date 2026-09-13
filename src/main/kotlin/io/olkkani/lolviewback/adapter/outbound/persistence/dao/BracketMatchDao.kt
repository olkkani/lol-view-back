package io.olkkani.lolviewback.adapter.outbound.persistence.dao

import io.hypersistence.tsid.TSID
import io.olkkani.lolviewback.adapter.outbound.client.bracket.dto.PandaScoreMatch
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.BracketMatch
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.generated.Tables
import org.jooq.generated.tables.records.BracketMatchesRecord
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class BracketMatchDao(
    private val dsl: DSLContext,
) {
    private val bracketMatches = Tables.BRACKET_MATCHES

    /**
     * Upserts by (tournament_id, match_id): unlike MatchPollingDao's
     * upsertMatches (ON CONFLICT DO NOTHING, since a re-polled lolesports
     * match never changes), this uses DO UPDATE — a re-synced bracket match
     * can transition status/winner/scores between syncs (e.g. not_started ->
     * finished, gaining a winner), so stale data must be overwritten, not
     * skipped.
     */
    fun upsertMatches(
        tournamentId: String,
        matchesToSave: List<PandaScoreMatch>,
    ): List<BracketMatch> {
        if (matchesToSave.isEmpty()) return emptyList()

        matchesToSave.forEach { match ->
            dsl.insertInto(
                bracketMatches,
                bracketMatches.ID,
                bracketMatches.TOURNAMENT_ID,
                bracketMatches.MATCH_ID,
                bracketMatches.NAME,
                bracketMatches.STATUS,
                bracketMatches.SLUG,
                bracketMatches.WINNER_ID,
                bracketMatches.WINNER_TYPE,
                bracketMatches.BEGIN_AT,
                bracketMatches.END_AT,
                bracketMatches.SCHEDULED_AT,
                bracketMatches.PREVIOUS_MATCHES,
                bracketMatches.OPPONENTS,
                bracketMatches.RESULTS,
            ).values(
                TSID.Factory.getTsid().toLong(),
                tournamentId,
                match.id,
                match.name,
                match.status,
                match.slug,
                match.winnerId,
                match.winnerType,
                match.beginAt?.let { OffsetDateTime.parse(it).toLocalDateTime() },
                match.endAt?.let { OffsetDateTime.parse(it).toLocalDateTime() },
                match.scheduledAt?.let { OffsetDateTime.parse(it).toLocalDateTime() },
                JSONB.valueOf(match.previousMatches.toString()),
                JSONB.valueOf(match.opponents.toString()),
                JSONB.valueOf(match.results.toString()),
            )
                .onConflict(bracketMatches.TOURNAMENT_ID, bracketMatches.MATCH_ID)
                .doUpdate()
                .set(bracketMatches.NAME, match.name)
                .set(bracketMatches.STATUS, match.status)
                .set(bracketMatches.WINNER_ID, match.winnerId)
                .set(bracketMatches.WINNER_TYPE, match.winnerType)
                .set(bracketMatches.BEGIN_AT, match.beginAt?.let { OffsetDateTime.parse(it).toLocalDateTime() })
                .set(bracketMatches.END_AT, match.endAt?.let { OffsetDateTime.parse(it).toLocalDateTime() })
                .set(bracketMatches.PREVIOUS_MATCHES, JSONB.valueOf(match.previousMatches.toString()))
                .set(bracketMatches.OPPONENTS, JSONB.valueOf(match.opponents.toString()))
                .set(bracketMatches.RESULTS, JSONB.valueOf(match.results.toString()))
                .execute()
        }

        val matchIds = matchesToSave.map { it.id }
        return dsl.selectFrom(bracketMatches)
            .where(bracketMatches.TOURNAMENT_ID.eq(tournamentId))
            .and(bracketMatches.MATCH_ID.`in`(matchIds))
            .fetch { record -> recordToBracketMatch(record) }
    }

    /**
     * Maps a jOOQ bracket_matches Record to a BracketMatch entity. Extracted
     * so Task 8's findByTournamentId (a read method on this same DAO) can
     * reuse the exact same mapping instead of duplicating it.
     */
    private fun recordToBracketMatch(record: BracketMatchesRecord): BracketMatch =
        BracketMatch(
            id = record.id!!,
            tournamentId = record.tournamentId!!,
            matchId = record.matchId!!,
            name = record.name!!,
            status = record.status!!,
            slug = record.slug,
            winnerId = record.winnerId,
            winnerType = record.winnerType,
            beginAt = record.beginAt,
            endAt = record.endAt,
            scheduledAt = record.scheduledAt,
            previousMatches = record.previousMatches!!.data(),
            opponents = record.opponents!!.data(),
            results = record.results!!.data(),
        )
}
