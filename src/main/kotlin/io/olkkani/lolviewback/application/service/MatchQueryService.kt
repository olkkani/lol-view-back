package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.adapter.inbound.web.dto.MatchResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.toResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchParticipantRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchSetDao
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

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
}
