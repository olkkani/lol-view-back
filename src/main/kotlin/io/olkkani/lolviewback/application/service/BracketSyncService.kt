package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.client.bracket.PandaScoreClient
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.BracketMatchDao
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.TournamentDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class BracketSyncService(
    private val tournamentRepository: TournamentRepository,
    private val tournamentDao: TournamentDao,
    private val externalIdLookupService: ExternalIdLookupService,
    private val pandaScoreClient: PandaScoreClient,
    private val bracketMatchDao: BracketMatchDao,
    private val nowProvider: () -> Instant = Instant::now,
    private val log: Logger = LoggerFactory.getLogger(BracketSyncService::class.java),
) {
    // Log-spam guard: an unmapped or persistently-failing tournament must not
    // log once per 5-minute tick forever. Rate-limited to once per hour per
    // lolesports tournament id.
    private val lastFailureLoggedAt = ConcurrentHashMap<String, Instant>()
    private val logSuppressionWindow = Duration.ofHours(1)

    suspend fun syncAllMappedTournaments() {
        val tournamentIds =
            withContext(Dispatchers.IO) {
                tournamentDao.findTournamentIdsWithBothProviders(Provider.LOLESPORTS, Provider.PANDASCORE)
            }
        val tournaments =
            withContext(Dispatchers.IO) {
                tournamentRepository.findAllById(tournamentIds)
            }

        for (tournament in tournaments) {
            val lolesportsId: String
            val pandascoreId: String
            try {
                lolesportsId = externalIdLookupService.resolveTournament(tournament, Provider.LOLESPORTS)
                pandascoreId = externalIdLookupService.resolveTournament(tournament, Provider.PANDASCORE)
            } catch (e: MissingExternalIdException) {
                log.warn("Skipping tournament {} — {}", tournament.id, e.message)
                continue
            }
            try {
                val matches = pandaScoreClient.fetchBrackets(pandascoreId)
                withContext(Dispatchers.IO) {
                    // TODO: look up and pass stageId — upsertMatches currently nulls curated stage links
                    bracketMatchDao.upsertMatches(lolesportsId, matches)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailureRateLimited(lolesportsId, e)
            }
        }
    }

    /**
     * Returns true if a failure for [lolesportsTournamentId] should be logged
     * right now (first-ever failure, or the suppression window has elapsed
     * since the last logged failure), and records that a log just happened
     * when it returns true. Extracted from [logFailureRateLimited] so the
     * suppression decision itself can be unit-tested without asserting on
     * log output.
     */
    private fun shouldLogFailureNow(lolesportsTournamentId: String): Boolean {
        val now = nowProvider()
        val lastLogged = lastFailureLoggedAt[lolesportsTournamentId]
        val shouldLog = lastLogged == null || Duration.between(lastLogged, now) > logSuppressionWindow
        if (shouldLog) {
            lastFailureLoggedAt[lolesportsTournamentId] = now
        }
        return shouldLog
    }

    private fun logFailureRateLimited(
        lolesportsTournamentId: String,
        e: Exception,
    ) {
        if (shouldLogFailureNow(lolesportsTournamentId)) {
            log.error("Failed to sync bracket for tournament $lolesportsTournamentId, skipping", e)
        }
    }
}
