package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchParticipant
import org.springframework.data.jpa.repository.JpaRepository

interface MatchParticipantRepository : JpaRepository<MatchParticipant, Long> {
    fun findByMatchIdIn(matchIds: List<Long>): List<MatchParticipant>
}
