package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchParticipant
import org.springframework.data.jpa.repository.JpaRepository

interface MatchParticipantRepository : JpaRepository<MatchParticipant, Long> {
    fun findByMatchIdIn(matchIds: List<Long>): List<MatchParticipant>
}
