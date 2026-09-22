package io.olkkani.lolviewback.adapter.outbound.persistence.dao

import io.hypersistence.tsid.TSID
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.BracketStage
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.generated.Tables
import org.jooq.generated.tables.records.BracketStagesRecord
import org.springframework.stereotype.Repository

@Repository
class BracketStageDao(
    private val dsl: DSLContext,
) {
    companion object {
        private val VALID_FORMATS = setOf("ROUND_ROBIN", "SWISS", "SINGLE_ELIMINATION", "DOUBLE_ELIMINATION")
    }

    private val bracketStages = Tables.BRACKET_STAGES

    /**
     * Upserts by (tournament_id, ordinal): stage curation is manual (no
     * PandaScore sync source), so a re-curated stage at the same position
     * overwrites in place rather than duplicating.
     */
    fun upsert(stage: BracketStage): BracketStage {
        require(stage.format in VALID_FORMATS) {
            "Unrecognized bracket stage format: ${stage.format} " +
                "(expected one of: ${VALID_FORMATS.joinToString(", ")})"
        }

        dsl.insertInto(
            bracketStages,
            bracketStages.ID,
            bracketStages.TOURNAMENT_ID,
            bracketStages.NAME,
            bracketStages.FORMAT,
            bracketStages.ORDINAL,
            bracketStages.CONFIG,
        ).values(
            TSID.Factory.getTsid().toLong(),
            stage.tournamentId,
            stage.name,
            stage.format,
            stage.ordinal,
            stage.config?.let { JSONB.valueOf(it) },
        )
            .onConflict(bracketStages.TOURNAMENT_ID, bracketStages.ORDINAL)
            .doUpdate()
            .set(bracketStages.NAME, stage.name)
            .set(bracketStages.FORMAT, stage.format)
            .set(bracketStages.CONFIG, stage.config?.let { JSONB.valueOf(it) })
            .execute()

        return dsl.selectFrom(bracketStages)
            .where(bracketStages.TOURNAMENT_ID.eq(stage.tournamentId))
            .and(bracketStages.ORDINAL.eq(stage.ordinal))
            .fetchOne { record -> recordToBracketStage(record) }
            ?: error("upsert did not persist a row for tournament=${stage.tournamentId} ordinal=${stage.ordinal}")
    }

    fun findByTournamentId(tournamentId: String): List<BracketStage> =
        dsl.selectFrom(bracketStages)
            .where(bracketStages.TOURNAMENT_ID.eq(tournamentId))
            .orderBy(bracketStages.ORDINAL)
            .fetch { record -> recordToBracketStage(record) }

    private fun recordToBracketStage(record: BracketStagesRecord): BracketStage =
        BracketStage(
            id = record.id!!,
            tournamentId = record.tournamentId!!,
            name = record.name!!,
            format = record.format!!,
            ordinal = record.ordinal!!,
            config = record.config?.data(),
        )
}
