package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueNotFoundException
import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueRequest
import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.toResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRecurrenceWindowRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueExternalId
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueRecurrenceWindow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeagueService(
    private val leagueRepository: LeagueRepository,
    private val leagueRecurrenceWindowRepository: LeagueRecurrenceWindowRepository,
    private val leagueExternalIdRepository: LeagueExternalIdRepository,
) {
    @Transactional
    fun create(request: LeagueRequest): LeagueResponse {
        val league =
            leagueRepository.save(
                League(
                    leagueName = request.leagueName,
                    logoUrl = request.logoUrl,
                    isActive = true,
                    logoBackdrop = request.logoBackdrop,
                ),
            )

        val recurrenceWindows =
            request.recurrenceWindows.map { window ->
                leagueRecurrenceWindowRepository.save(
                    LeagueRecurrenceWindow(
                        label = window.label,
                        tieBreakPriority = window.tieBreakPriority,
                        intervalYear = window.intervalYear,
                        startDate = window.startDate,
                        league = league,
                    ),
                )
            }

        val externalIds =
            request.externalIds.map { externalId ->
                // saveAndFlush (not save) so a unique-constraint violation on (entityId, provider)
                // surfaces here, inside this @Transactional method, rather than staying buffered
                // until a later, unrelated flush - the whole create() must still roll back together.
                leagueExternalIdRepository.saveAndFlush(
                    LeagueExternalId(
                        entityId = league.id,
                        provider = externalId.provider,
                        externalId = externalId.externalId,
                    ),
                )
            }

        return league.toResponse(recurrenceWindows, externalIds)
    }

    fun getAll(): List<LeagueResponse> {
        val leagues = leagueRepository.findAll()
        val windowsByLeagueId =
            leagueRecurrenceWindowRepository.findByLeagueIn(leagues).groupBy { it.league.id }
        val externalIdsByLeagueId =
            leagueExternalIdRepository.findByEntityIdIn(leagues.map { it.id }).groupBy { it.entityId }
        return leagues.map { league ->
            league.toResponse(
                windowsByLeagueId[league.id].orEmpty(),
                externalIdsByLeagueId[league.id].orEmpty(),
            )
        }
    }

    fun getById(id: Long): LeagueResponse {
        val league = findOrThrow(id)
        return league.toResponse(
            leagueRecurrenceWindowRepository.findByLeague(league),
            leagueExternalIdRepository.findByEntityId(id),
        )
    }

    fun update(
        id: Long,
        request: LeagueRequest,
    ): LeagueResponse {
        val league = findOrThrow(id)
        league.leagueName = request.leagueName
        league.logoUrl = request.logoUrl
        league.logoBackdrop = request.logoBackdrop
        val saved = leagueRepository.save(league)
        return saved.toResponse(
            leagueRecurrenceWindowRepository.findByLeague(saved),
            leagueExternalIdRepository.findByEntityId(id),
        )
    }

    fun delete(id: Long) {
        val league = findOrThrow(id)
        league.isActive = false
        leagueRepository.save(league)
    }

    private fun findOrThrow(id: Long): League =
        leagueRepository.findById(id).orElseThrow { LeagueNotFoundException("League not found: $id") }
}
