package io.olkkani.lolviewback.adapter.inbound.web.dto

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueExternalId
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LogoBackdrop
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider

data class LeagueResponse(
    val id: Long,
    val leagueName: String,
    val logoUrl: String,
    val isActive: Boolean,
    val logoBackdrop: LogoBackdrop?,
    val externalIds: List<ExternalIdResponse>,
)

data class ExternalIdResponse(
    val provider: Provider,
    val externalId: String,
)

data class LeagueRequest(
    val leagueName: String,
    val logoUrl: String,
    val logoBackdrop: LogoBackdrop?,
    val externalIds: List<ExternalIdRequest> = emptyList(),
)

data class ExternalIdRequest(
    val provider: Provider,
    val externalId: String,
)

class LeagueNotFoundException(
    message: String,
) : NoSuchElementException(message)

fun League.toResponse(
    externalIds: List<LeagueExternalId>,
): LeagueResponse =
    LeagueResponse(
        id = this.id,
        leagueName = this.leagueName,
        logoUrl = this.logoUrl,
        isActive = this.isActive,
        logoBackdrop = this.logoBackdrop,
        externalIds = externalIds.map { it.toResponse() },
    )

fun LeagueExternalId.toResponse(): ExternalIdResponse =
    ExternalIdResponse(
        provider = this.provider,
        externalId = this.externalId,
    )
