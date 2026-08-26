package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.projection.ClubWithFollowProjection
import org.jooq.DSLContext
import org.jooq.generated.Tables
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ClubProfileJooqRepository(
    private val dsl: DSLContext,
) {
    private val userFollowedClubs = Tables.USER_FOLLOWED_CLUBS
    private val clubProfiles = Tables.CLUB_PROFILES

    fun findAllActive(
        userId: Long,
        today: LocalDate = LocalDate.now(),
    ): List<ClubWithFollowProjection> {
        val isFollowed =
            DSL
                .`when`(userFollowedClubs.CLUB_ID.isNotNull, true)
                .otherwise(false)
                .`as`("is_followed")

        return dsl
            .select(
                clubProfiles.ID,
                clubProfiles.CLUB_ID,
                clubProfiles.CLUB_NAME,
                clubProfiles.ABBREVIATION,
                clubProfiles.LOGO_URL,
                clubProfiles.REGION,
                isFollowed,
            ).from(clubProfiles)
            .leftJoin(userFollowedClubs)
            .on(
                clubProfiles.CLUB_ID
                    .eq(userFollowedClubs.CLUB_ID)
                    .and(userFollowedClubs.USER_ID.eq(userId)),
            ).where(clubProfiles.EFFECTIVE_FROM.le(today))
            .and(clubProfiles.EFFECTIVE_TO.isNull.or(clubProfiles.EFFECTIVE_TO.gt(today)))
            .fetch { record ->
                ClubWithFollowProjection(
                    clubProfileId = requireNotNull(record.get(clubProfiles.ID)),
                    clubId = record.get(clubProfiles.CLUB_ID),
                    clubName = record.get(clubProfiles.CLUB_NAME),
                    abbreviation = record.get(clubProfiles.ABBREVIATION),
                    logoUrl = record.get(clubProfiles.LOGO_URL),
                    region = record.get(clubProfiles.REGION),
                    isFollowed = requireNotNull(record.get(isFollowed)),
                )
            }
    }
}
