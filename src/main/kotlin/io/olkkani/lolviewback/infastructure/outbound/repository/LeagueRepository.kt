package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import org.springframework.data.jpa.repository.JpaRepository

interface LeagueRepository : JpaRepository<League, Long> {
    fun findByLeagueApiId(leagueApiId: String): League?
    fun findByIsActiveTrue(): List<League>
}
