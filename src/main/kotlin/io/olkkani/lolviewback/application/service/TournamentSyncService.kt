package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.client.sync.LolEsportsApiClient
import io.olkkani.lolviewback.adapter.outbound.client.sync.mapper.toEntity
import io.olkkani.lolviewback.adapter.outbound.persistence.LeagueRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentExternalIdRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.TournamentExternalId
import io.olkkani.lolviewback.application.outbound.ProviderExternalId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TournamentSyncService(
    private val leagueRepository: LeagueRepository,
    private val tournamentRepository: TournamentRepository,
    private val tournamentExternalIdRepository: TournamentExternalIdRepository,
    private val apiClient: LolEsportsApiClient,
    private val externalIdLookupService: ExternalIdLookupService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun syncNewTournaments() {
        log.info("Starting tournament sync")
        val activeLeagues = leagueRepository.findByIsActiveTrue()
        val lolesportsIdsByLeagueId = externalIdLookupService.resolveAllLeagues(activeLeagues, Provider.LOLESPORTS)

        for (league in activeLeagues) {
            val lolesportsId = lolesportsIdsByLeagueId[league.id]
            if (lolesportsId == null) {
                log.warn("League {} has no LOLESPORTS mapping — skipping sync for this league", league.id)
                continue
            }

            val existingTournaments = tournamentRepository.findAll().filter { it.league.id == league.id }
            val existingLolesportsIds =
                externalIdLookupService.resolveAllTournaments(existingTournaments, Provider.LOLESPORTS).values.toSet()

            try {
                val fetched = apiClient.fetchTournaments(ProviderExternalId(Provider.LOLESPORTS, lolesportsId))
                for (apiResponse in fetched) {
                    if (apiResponse.apiId in existingLolesportsIds) continue
                    val savedTournament = tournamentRepository.save(apiResponse.toEntity(league))
                    tournamentExternalIdRepository.save(
                        TournamentExternalId(
                            entityId = savedTournament.id,
                            provider = Provider.LOLESPORTS,
                            externalId = apiResponse.apiId,
                        ),
                    )
                }
            } catch (e: MissingExternalIdException) {
                log.warn("Skipping league {} — {}", league.id, e.message)
            } catch (e: Exception) {
                log.error("Failed to fetch tournaments for league {}", league.id, e)
            }
        }
        log.info("Finished tournament sync")
    }
}
