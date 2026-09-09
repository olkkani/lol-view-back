package io.olkkani.lolviewback.adapter.outbound.persistence.dao

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LogoBackdrop
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import org.jooq.DSLContext
import org.jooq.generated.Tables
import org.jooq.generated.tables.records.LeaguesRecord
import org.jooq.generated.tables.records.TournamentsRecord
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class TournamentPollingDao(
    private val dsl: DSLContext,
) {
    private val tournament = Tables.TOURNAMENTS
    private val league = Tables.LEAGUES

    fun findInProgressTournaments(): List<Tournament> {
        val today = LocalDate.now()
        return dsl.select(tournament, league)
            .from(tournament)
            .join(league).on(tournament.LEAGUE_ID.eq(league.ID))
            .where(
                tournament.START_DATE.le(today.plusDays(7))
                    .and(tournament.END_DATE.ge(today))
            )
            .fetch { record ->
                record.value1().toEntity(record.value2().toEntity())
            }
    }

    private fun LeaguesRecord.toEntity() =
        League(
            id = id,
            leagueName = leagueName,
            logoUrl = logoUrl,
            isActive = isActive,
            leagueApiId = leagueApiId,
            logoBackdrop = logoBackdrop?.let { LogoBackdrop.valueOf(it) },
        )

    private fun TournamentsRecord.toEntity(league: League) =
        Tournament(
            id = id,
            tournamentName = tournamentName,
            startDate = startDate,
            endDate = endDate,
            tournamentApiId = tournamentApiId,
            league = league,
        )
}