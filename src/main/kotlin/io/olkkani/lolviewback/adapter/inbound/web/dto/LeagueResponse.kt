package io.olkkani.lolviewback.adapter.inbound.web.dto

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueExternalId
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueRecurrenceWindow
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LogoBackdrop
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import java.time.LocalDate

data class LeagueResponse(
    val id: Long,
    val leagueName: String,
    val logoUrl: String,
    val isActive: Boolean,
    val logoBackdrop: LogoBackdrop?,
    val recurrenceWindows: List<RecurrenceWindowResponse>,
    val externalIds: List<ExternalIdResponse>,
)

data class RecurrenceWindowResponse(
    val id: Long,
    val label: String,
    val tieBreakPriority: Int,
    val intervalYear: Int,
    val startDate: LocalDate,
)

data class ExternalIdResponse(
    val provider: Provider,
    val externalId: String,
)

data class LeagueRequest(
    val leagueName: String,
    val logoUrl: String,
    val logoBackdrop: LogoBackdrop?,
    val recurrenceWindows: List<RecurrenceWindowRequest> = emptyList(),
    val externalIds: List<ExternalIdRequest> = emptyList(),
)

data class RecurrenceWindowRequest(
    val label: String,
    val tieBreakPriority: Int,
    val intervalYear: Int,
    val startDate: LocalDate,
)

data class ExternalIdRequest(
    val provider: Provider,
    val externalId: String,
)

class LeagueNotFoundException(
    message: String,
) : NoSuchElementException(message)

fun League.toResponse(
    recurrenceWindows: List<LeagueRecurrenceWindow>,
    externalIds: List<LeagueExternalId>,
): LeagueResponse =
    LeagueResponse(
        id = this.id,
        leagueName = this.leagueName,
        logoUrl = this.logoUrl,
        isActive = this.isActive,
        logoBackdrop = this.logoBackdrop,
        recurrenceWindows = recurrenceWindows.map { it.toResponse() },
        externalIds = externalIds.map { it.toResponse() },
    )

fun LeagueRecurrenceWindow.toResponse(): RecurrenceWindowResponse =
    RecurrenceWindowResponse(
        id = this.id,
        label = this.label,
        tieBreakPriority = this.tieBreakPriority,
        intervalYear = this.intervalYear,
        startDate = this.startDate,
    )

fun LeagueExternalId.toResponse(): ExternalIdResponse =
    ExternalIdResponse(
        provider = this.provider,
        externalId = this.externalId,
    )
