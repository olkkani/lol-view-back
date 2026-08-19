package io.olkkani.lolviewback.infastructure.outbound.repository.projection

data class ClubWithFollowProjection(
    val clubProfileId: Long,
    val clubId: Long?,
    val clubName: String?,
    val abbreviation: String?,
    val logoUrl: String?,
    val region: String?,
    val isFollowed: Boolean,
)
