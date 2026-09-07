package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchApiState
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEvent
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.toEntity
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.toProfileEntity
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchParticipantRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.MatchRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.ClubProfileRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.TournamentPollingDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubProfile
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchParticipant
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Tournament
import io.olkkani.lolviewback.application.outbound.LolApiClientPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PollMatchDataService(
    private val tournamentRepository: TournamentRepository,
    private val tournamentPollingDao: TournamentPollingDao,
    private val matchRepository: MatchRepository,
    private val clubProfileRepository: ClubProfileRepository,
    private val matchParticipantRepository: MatchParticipantRepository,
    private val apiClientPort: LolApiClientPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun syncUpcomingMatches() {
        for (inProgressTournament in tournamentPollingDao.findInProgressTournaments()) {
            syncTournament(inProgressTournament)
        }
    }

    private suspend fun syncTournament(inProgressTournament: Tournament) {
        val unstartedMatches =
            apiClientPort
                .fetchMatches(inProgressTournament.league.leagueApiId)
                .filter { it.state == MatchApiState.UNSTARTED.toString() }
        if (unstartedMatches.isEmpty()) return

        val tournamentRef =
            withContext(Dispatchers.IO) {
                tournamentRepository.getReferenceById(inProgressTournament.id)
            }

        val savedMatches =
            withContext(Dispatchers.IO) {
                matchRepository.saveAll(unstartedMatches.map { it.toEntity(tournamentRef) })
            }

        val profilesByAbbreviation = resolveClubProfiles(unstartedMatches)

        val participants =
            unstartedMatches.zip(savedMatches).flatMap { (apiMatch, savedMatch) ->
                apiMatch.match.teams.map { team ->
                    val profile = profilesByAbbreviation.getValue(team.code)
                    MatchParticipant(match = savedMatch, club = profile.club, clubProfile = profile)
                }
            }

        withContext(Dispatchers.IO) {
            matchParticipantRepository.saveAll(participants)
        }
    }

    /**
     * Resolves one [ClubProfile] per distinct team abbreviation across all matches in a
     * single batch: one lookup query, one insert for any missing profiles — instead of a
     * round trip per team per match.
     */
    private suspend fun resolveClubProfiles(matches: List<MatchScheduleEvent>): Map<String, ClubProfile> {
        val teamsByAbbreviation = matches.flatMap { it.match.teams }.associateBy { it.code }

        val existingProfiles =
            withContext(Dispatchers.IO) {
                clubProfileRepository.findByAbbreviationIn(teamsByAbbreviation.keys)
            }.associateBy { it.abbreviation }

        val missingTeams = teamsByAbbreviation.filterKeys { it !in existingProfiles }.values
        if (missingTeams.isEmpty()) return existingProfiles

        // TODO: New Club Alert
        val newProfiles =
            withContext(Dispatchers.IO) {
                clubProfileRepository.saveAll(missingTeams.map { it.toProfileEntity() })
            }.associateBy { it.abbreviation }

        return existingProfiles + newProfiles
    }
}
