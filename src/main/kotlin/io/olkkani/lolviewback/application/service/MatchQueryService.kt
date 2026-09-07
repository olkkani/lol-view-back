package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.inbound.web.dto.HeadToHeadResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.InvalidHeadToHeadRequestException
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchNotFoundException
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.toResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchParticipantRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchSetDao
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

private val KST: ZoneId = ZoneId.of("Asia/Seoul")
private val logger = LoggerFactory.getLogger(MatchQueryService::class.java)

@Service
class MatchQueryService(
    private val matchRepository: MatchRepository,
    private val matchParticipantRepository: MatchParticipantRepository,
    private val matchSetDao: MatchSetDao,
) {
    fun findMatches(
        range: MatchRange,
        today: LocalDate = LocalDate.now(KST),
    ): List<MatchResponse> {
        val (start, end) = range.toDateRange(today)
        val matches =
            matchRepository
                .findByStartTimeGreaterThanEqualAndStartTimeLessThan(start, end)
                .sortedBy { it.startTime }

        val matchIds = matches.mapNotNull { it.id }
        val participantsByMatchId =
            matchParticipantRepository
                .findByMatchIdIn(matchIds)
                .groupBy { it.match.id }

        val matchDates = matches.associate { it.id to it.startTime.toLocalDate() }
        val scoresByMatchId =
            matchSetDao
                .findWinCountsByMatchIdIn(matchIds, matchDates)
                .groupBy { it.matchId }
                .mapValues { (_, projections) -> projections.associate { it.clubId to it.wins } }

        return matches.map { match ->
            match.toResponse(
                participantsByMatchId[match.id].orEmpty(),
                scoresByMatchId[match.id].orEmpty(),
            )
        }
    }

    fun findHeadToHead(matchId: Long): List<HeadToHeadResponse> {
        val currentMatch = matchRepository.findById(matchId)
            .orElseThrow { MatchNotFoundException("Match not found: $matchId") }

        val currentParticipants = matchParticipantRepository.findByMatchIdIn(listOf(matchId))
        val clubIds = currentParticipants.mapNotNull { it.club?.id }.distinct()
        if (clubIds.size != 2) {
            throw InvalidHeadToHeadRequestException(
                "Match $matchId does not have exactly 2 distinct club participants: found $clubIds",
            )
        }
        val (clubId1, clubId2) = clubIds[0] to clubIds[1]

        val matches = matchRepository.findHeadToHeadBefore(
            clubId1, clubId2, currentMatch.startTime, PageRequest.of(0, 5),
        )
        if (matches.isEmpty()) return emptyList()

        val matchIds = matches.mapNotNull { it.id }
        val matchDates = matches.associate { it.id to it.startTime.toLocalDate() }
        val winsByMatchId = matchSetDao.findWinCountsByMatchIdIn(matchIds, matchDates)
            .groupBy { it.matchId }

        return matches
            .reversed() // repository fetches newest-first for LIMIT; response is oldest-first
            .mapNotNull { match ->
                val winnerClubId = winsByMatchId[match.id]?.maxByOrNull { it.wins }?.clubId
                if (winnerClubId == null) {
                    logger.warn("Match {} is COMPLETED but has no synced MatchSet rows; excluding from head-to-head", match.id)
                    null
                } else {
                    HeadToHeadResponse(matchId = requireNotNull(match.id), winnerClubId = winnerClubId)
                }
            }
    }
}
