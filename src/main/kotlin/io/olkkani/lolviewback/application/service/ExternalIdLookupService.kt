package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.persistence.ClubExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Club
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import org.springframework.stereotype.Service

@Service
class ExternalIdLookupService(
    private val leagueExternalIdRepository: LeagueExternalIdRepository,
    private val tournamentExternalIdRepository: TournamentExternalIdRepository,
    private val clubExternalIdRepository: ClubExternalIdRepository,
) {
    fun resolveLeague(league: League, provider: Provider): String =
        leagueExternalIdRepository.findByEntityIdAndProvider(league.id, provider)?.externalId
            ?: throw MissingExternalIdException("League", league.id, provider)

    fun resolveTournament(tournament: Tournament, provider: Provider): String =
        tournamentExternalIdRepository.findByEntityIdAndProvider(tournament.id, provider)?.externalId
            ?: throw MissingExternalIdException("Tournament", tournament.id, provider)

    fun resolveClub(club: Club, provider: Provider): String =
        clubExternalIdRepository.findByEntityIdAndProvider(club.id, provider)?.externalId
            ?: throw MissingExternalIdException("Club", club.id, provider)

    fun resolveAllLeagues(leagues: List<League>, provider: Provider): Map<Long, String> {
        if (leagues.isEmpty()) return emptyMap()
        return leagueExternalIdRepository
            .findByEntityIdInAndProvider(leagues.map { it.id }, provider)
            .associate { it.entityId to it.externalId }
    }

    fun resolveAllTournaments(tournaments: List<Tournament>, provider: Provider): Map<Long, String> {
        if (tournaments.isEmpty()) return emptyMap()
        return tournamentExternalIdRepository
            .findByEntityIdInAndProvider(tournaments.map { it.id }, provider)
            .associate { it.entityId to it.externalId }
    }
}
