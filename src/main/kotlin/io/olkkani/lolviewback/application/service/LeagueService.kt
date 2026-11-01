package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueNotFoundException
import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueRequest
import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.toResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueExternalId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeagueService(
    private val leagueRepository: LeagueRepository,
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

        return league.toResponse(externalIds)
    }

    fun getAll(): List<LeagueResponse> {
        val leagues = leagueRepository.findAll()
        val externalIdsByLeagueId =
            leagueExternalIdRepository.findByEntityIdIn(leagues.map { it.id }).groupBy { it.entityId }
        return leagues.map { league ->
            league.toResponse(externalIdsByLeagueId[league.id].orEmpty())
        }
    }

    fun getById(id: Long): LeagueResponse {
        val league = findOrThrow(id)
        return league.toResponse(leagueExternalIdRepository.findByEntityId(id))
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
        return saved.toResponse(leagueExternalIdRepository.findByEntityId(id))
    }

    fun delete(id: Long) {
        val league = findOrThrow(id)
        league.isActive = false
        leagueRepository.save(league)
    }

    private fun findOrThrow(id: Long): League =
        leagueRepository.findById(id).orElseThrow { LeagueNotFoundException("League not found: $id") }
}
