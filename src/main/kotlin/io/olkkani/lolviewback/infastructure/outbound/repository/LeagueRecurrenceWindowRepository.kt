package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.LeagueRecurrenceWindow
import org.springframework.data.jpa.repository.JpaRepository

interface LeagueRecurrenceWindowRepository : JpaRepository<LeagueRecurrenceWindow, Long> {
    fun findByLeague(league: League): List<LeagueRecurrenceWindow>
}
