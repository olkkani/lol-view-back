package io.olkkani.lolviewback.adapter.outbound.persistence.dao

import io.olkkani.lolviewback.adapter.outbound.persistence.projection.MatchSetProjection
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.generated.Tables
import org.jooq.impl.DSL.count
import org.springframework.stereotype.Repository
import java.time.LocalDate
import kotlin.jvm.java

@Repository
class MatchSetDao(
    private val dsl: DSLContext,
) {
    private val matchSet = Tables.MATCH_SETS
    private val clubProfiles = Tables.CLUB_PROFILES

    fun findWinCountsByMatchIdIn(
        matchIds: List<Long>,
        today: LocalDate = LocalDate.now(),
    ): List<MatchSetProjection> {
        if (matchIds.isEmpty()) return emptyList()

        val winsField: Field<Int> = count().`as`("wins")

        return dsl
            .select(
                matchSet.MATCH_ID,
                matchSet.SET_WIN_CLUB_ID,
                clubProfiles.ABBREVIATION,
                winsField,
            ).from(matchSet)
            .leftJoin(clubProfiles)
            .on(
                clubProfiles.CLUB_ID.eq(matchSet.SET_WIN_CLUB_ID)
                    .and(clubProfiles.EFFECTIVE_FROM.le(today))
                    .and(clubProfiles.EFFECTIVE_TO.isNull.or(clubProfiles.EFFECTIVE_TO.gt(today))),
            ).where(matchSet.MATCH_ID.`in`(matchIds))
            .groupBy(matchSet.MATCH_ID, matchSet.SET_WIN_CLUB_ID, clubProfiles.ABBREVIATION)
            .fetch { record ->
                MatchSetProjection(
                    matchId = requireNotNull(record.get(matchSet.MATCH_ID)),
                    clubId = requireNotNull(record.get(matchSet.SET_WIN_CLUB_ID)),
                    abbreviation = record.get(clubProfiles.ABBREVIATION),
                    wins = requireNotNull(record.get(winsField)),
                )
            }
    }
}
