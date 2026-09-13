package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.client.bracket.PandaScoreClient
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentProviderMappingRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.BracketMatchDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class BracketSyncService(
    private val tournamentProviderMappingRepository: TournamentProviderMappingRepository,
    private val pandaScoreClient: PandaScoreClient,
    private val bracketMatchDao: BracketMatchDao,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Log-spam guard: an unmapped or persistently-failing tournament must not
    // log once per 5-minute tick forever. Rate-limited to once per hour per
    // lolesports tournament id.
    private val lastFailureLoggedAt = ConcurrentHashMap<String, Instant>()
    private val logSuppressionWindow = Duration.ofHours(1)

    suspend fun syncAllMappedTournaments() {
        val mappings =
            withContext(Dispatchers.IO) {
                tournamentProviderMappingRepository.findAll()
            }

        for (mapping in mappings) {
            try {
                val matches = pandaScoreClient.fetchBrackets(mapping.pandascoreTournamentId)
                withContext(Dispatchers.IO) {
                    bracketMatchDao.upsertMatches(mapping.lolesportsTournamentId, matches)
                }
            } catch (e: Exception) {
                logFailureRateLimited(mapping.lolesportsTournamentId, e)
            }
        }
    }

    private fun logFailureRateLimited(
        lolesportsTournamentId: String,
        e: Exception,
    ) {
        val now = Instant.now()
        val lastLogged = lastFailureLoggedAt[lolesportsTournamentId]
        if (lastLogged == null || Duration.between(lastLogged, now) > logSuppressionWindow) {
            log.error("Failed to sync bracket for tournament $lolesportsTournamentId, skipping", e)
            lastFailureLoggedAt[lolesportsTournamentId] = now
        }
    }
}
