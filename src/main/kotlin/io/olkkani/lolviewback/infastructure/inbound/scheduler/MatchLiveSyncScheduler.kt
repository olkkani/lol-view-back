package io.olkkani.lolviewback.infastructure.inbound.scheduler

import io.olkkani.lolviewback.infastructure.outbound.client.sync.LolEsportsApiClient
import io.olkkani.lolviewback.infastructure.outbound.repository.MatchRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MatchLiveSyncScheduler(
    private val matchRepository: MatchRepository,
    private val apiClient: LolEsportsApiClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 5 * 60 * 1000)
    fun syncOngoingMatches() {
        val ongoingMatches = matchRepository.findByMatchState(MatchState.ONGOING)
        for (match in ongoingMatches) {
            try {
                val detail =
                    runBlocking {
                        apiClient.fetchMatchDetail(match.matchApiId, match.tournament.league.leagueApiId)
                    }
                if (detail == null) {
                    log.info(
                        "Match {} not found in league schedule during live sync — skipping this cycle",
                        match.matchApiId,
                    )
                    continue
                }
                match.matchState = mapState(detail.state)
                matchRepository.save(match)
            } catch (e: Exception) {
                log.error("Failed to refresh live match {}", match.matchApiId, e)
            }
        }
    }

    private fun mapState(apiState: String): MatchState = when (apiState.lowercase()) {
        "unstarted" -> MatchState.SCHEDULED
        "inprogress" -> MatchState.ONGOING
        "completed" -> MatchState.FINISHED
        else -> throw IllegalArgumentException("Unknown match state from API: $apiState")
    }
}
