package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import org.springframework.data.jpa.repository.JpaRepository

interface LeagueRepository : JpaRepository<League, Long> {
    fun findByLeagueApiId(leagueApiId: String): League?
    fun findByIsActiveTrue(): List<League>
}
