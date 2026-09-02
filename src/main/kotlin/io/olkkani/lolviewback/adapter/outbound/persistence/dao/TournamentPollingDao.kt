package io.olkkani.lolviewback.adapter.outbound.persistence.dao

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import org.jooq.DSLContext
import org.jooq.generated.Tables
import org.springframework.stereotype.Repository
import java.time.LocalDate
import kotlin.jvm.java

@Repository
class TournamentPollingDao(
    private val dsl: DSLContext,
) {
    private val tournament = Tables.TOURNAMENTS


    fun findInProgressTournaments(): List<Tournament> {
        val today = LocalDate.now()
        return dsl.selectFrom(tournament)
            .where(
                tournament.START_DATE.le(today.plusDays(7))
                    .and(tournament.END_DATE.ge(today))
            )
            .fetchInto(Tournament::class.java)
    }
}