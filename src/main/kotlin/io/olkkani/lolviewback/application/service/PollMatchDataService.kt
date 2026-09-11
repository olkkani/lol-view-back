package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchApiState
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEvent
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.toEntity
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.toProfileEntity
import io.olkkani.lolviewback.adapter.outbound.persistence.TournamentRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.ClubProfileRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.MatchPollingDao
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.TournamentPollingDao
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubProfile
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
    private val matchPollingDao: MatchPollingDao,
    private val clubProfileRepository: ClubProfileRepository,
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
                matchPollingDao.upsertMatches(unstartedMatches.map { it.toEntity(tournamentRef) })
            }
        val savedMatchByApiId = savedMatches.associateBy { it.matchApiId }

        val profilesByAbbreviation = resolveClubProfiles(unstartedMatches)

        val participants =
            unstartedMatches.flatMap { apiMatch ->
                val savedMatch = savedMatchByApiId.getValue(apiMatch.match.id)
                apiMatch.match.teams.map { team ->
                    val profile = profilesByAbbreviation.getValue(team.code)
                    MatchParticipant(match = savedMatch, club = profile.club, clubProfile = profile)
                }
            }

        withContext(Dispatchers.IO) {
            matchPollingDao.upsertParticipants(participants)
        }

        val tbdProfile = profilesByAbbreviation[TBD_ABBREVIATION]
        if (tbdProfile != null) {
            withContext(Dispatchers.IO) {
                matchPollingDao.deleteStaleTbdParticipants(savedMatches.map { it.id }, tbdProfile.id)
            }
        }
    }

    /**
     * Resolves one [ClubProfile] per distinct team abbreviation across all matches in a
     * single batch: one lookup query, one insert for any missing profiles — instead of a
     * round trip per team per match. The API's "TBD" code (an undecided bracket slot) is
     * special-cased to always resolve to the single pre-seeded canonical TBD profile
     * (migration V1.8) rather than being treated as a new team to create — this guarantees
     * exactly one stable club_profile_id for every TBD slot, across all matches and polls.
     */
    private suspend fun resolveClubProfiles(matches: List<MatchScheduleEvent>): Map<String, ClubProfile> {
        val teamsByAbbreviation = matches.flatMap { it.match.teams }.associateBy { it.code }
        val realTeamAbbreviations = teamsByAbbreviation.keys - TBD_ABBREVIATION

        val existingProfiles =
            withContext(Dispatchers.IO) {
                clubProfileRepository.findByAbbreviationIn(realTeamAbbreviations)
            }.associateBy { it.abbreviation }

        val missingTeams = teamsByAbbreviation.filterKeys { it != TBD_ABBREVIATION && it !in existingProfiles }.values
        val newProfiles = if (missingTeams.isEmpty()) {
            emptyMap()
        } else {
            // TODO: New Club Alert
            withContext(Dispatchers.IO) {
                clubProfileRepository.saveAll(missingTeams.map { it.toProfileEntity() })
            }.associateBy { it.abbreviation }
        }

        val resolved = existingProfiles + newProfiles
        if (TBD_ABBREVIATION !in teamsByAbbreviation) return resolved

        val tbdProfile = withContext(Dispatchers.IO) {
            clubProfileRepository.findByAbbreviation(TBD_ABBREVIATION)
        } ?: error("Canonical TBD club_profiles row is missing — migration V1.8 must have run")

        return resolved + (TBD_ABBREVIATION to tbdProfile)
    }

    private companion object {
        const val TBD_ABBREVIATION = "TBD"
    }
}
