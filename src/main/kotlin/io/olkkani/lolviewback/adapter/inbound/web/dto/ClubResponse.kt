package io.olkkani.lolviewback.adapter.inbound.web.dto

import io.olkkani.lolviewback.adapter.outbound.persistence.projection.ClubWithFollowProjection

data class ClubResponse(
    val id: Long,
    val name: String,
    val imageUrl: String,
    val region: String,
    val isFollowed: Boolean,
    val clubId: String,
)

fun ClubWithFollowProjection.toResponse(): ClubResponse {
    return ClubResponse(
        id = this.clubProfileId,
        name = this.abbreviation.orEmpty(),
        imageUrl = this.logoUrl.orEmpty(),
        region = this.region.orEmpty(),
        isFollowed = this.isFollowed,
        clubId = this.clubId?.toString().orEmpty(),
    )
}
