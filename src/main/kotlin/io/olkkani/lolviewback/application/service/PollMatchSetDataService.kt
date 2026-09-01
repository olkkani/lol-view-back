package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.client.sync.LolEsportsApiClient
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchSetApiResponse
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.SetState
import io.olkkani.lolviewback.adapter.outbound.persistence.ClubRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.ClubProfileRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchSetDao
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchSetRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchSet
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchState
import io.olkkani.lolviewback.adapter.outbound.persistence.projection.MatchSetProjection
import io.olkkani.lolviewback.application.outbound.LolApiClientPort
import io.olkkani.lolviewback.application.outbound.MatchRepositoryPort
import io.olkkani.lolviewback.application.outbound.MatchSetRepositoryPort
import org.jooq.generated.tables.Matches
import org.springframework.stereotype.Service

@Service
class PollMatchSetDataService(
//    private val apiClientPort: LolApiClientPort,
    private val matchRepository: MatchRepository,
    private val matchSetRepository: MatchSetRepository,
    private val clubRepository: ClubRepository,
    private val clubProfilesRepository: ClubProfileRepository,
    private val matchSetDao: MatchSetDao,
//    private val matchRepositoryPort: MatchRepositoryPort,
//    private val matchSetRepositoryPort: MatchSetRepositoryPort,
) {
    fun syncMatchSets() {




    }
//        val inProgressMatches = matchRepository.findAllByMatchState(MatchState.IN_PROGRESS)
//        val inProgressMatchSets: List<MatchSetProjection> =
//            matchSetDao.findWinCountsByMatchIdIn(inProgressMatches.map { it.id })
//
//
//
//
//        for (match in inProgressMatches) {
//            val apiResponse = apiClientPort.fetchMatchSetData(match.id.toString())
//            val recordedMatchSets = inProgressMatchSets.filter { it.matchId == match.id }
//            val completedSets = mutableListOf<MatchSet>()
//
//            //
////            clubProfilesRepository.findByAbbreviation(re2cordedMatchSets.first().clubId)
//
//            clubProfilesRepository.findAll()
//
//
//
//            val apiCompletedSetCount = apiResponse.match.games.count { it.state == SetState.COMPLETED }
//            val recordedCompletedSetCount = recordedMatchSets.sumOf { it.wins }
//
//
//
//            val homeClub = clubRepository.findByCode(
//            val awayClub = clubRepository.findByCode(apiResponse.match.teams.find { it.code == match.awayTeam }!!.clubCode)!!
//
//            if(recordedMatchSets.first().wins == )
//            apiResponse.match.teams.
//
//
//
//
//
//            if (apiCompletedSetCount > recordedCompletedSetCount) {
//
//
//
//
//
//
//
//                val completeSet = apiResponse.match.games.find { it.number == apiCompletedSetCount }?.let {
//                }
//
//            completedSets.add(
//                MatchSet(
//                    setApiId =
//                )
//            )
//
//            }
//        }
//
//        fun isSyncRecord(): Boolean {
//        }
//
//        fun fetchMatchSetData(matchId: Long): MatchSetApiResponse = apiClientPort.fetchMatchSetData()
//
//        fun getAllInProgressMatch(): List<Match> = matchRepositoryPort.findAllInProgress()
//
//        fun findAllGroupByMatchIds(matchIds: List<Long>): Map<Long, List<MatchSet>> =
//            matchSetRepositoryPort.findAllGroupByMatchIds(matchIds)
//
//        fun resolveNewlyCompletedGames() {
//            // todo
//        }
//
//        fun saveMatchSet(matchSet: MatchSet) {
//            matchSetRepositoryPort.save(matchSet)
//        }
//    }
//
////    fun pollMatchSetData(): MatchSetApiResponse {
//
////        val inProgressMatches = matchRepository.findAllByMatchState(MatchState.INPROGRESS)
//
////        for (match in inProgressMatches) {
////            apiClient.getMatchData(match.id)
//
////        }
//
////        return apiClient.getMatchSetData()
////    }
//
////    fun pollMatchSetData(): MatchSetApiResponse {
////        val matchSetApiResponse = apiClient.getMatchSetData()
////        matchSetRepositoryPort.save(matchSetApiResponse.matchSet)
////        return matchSetApiResponse
////    }
}
