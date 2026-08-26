package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.League
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LeagueRecurrenceWindow
import org.springframework.data.jpa.repository.JpaRepository

interface LeagueRecurrenceWindowRepository : JpaRepository<LeagueRecurrenceWindow, Long> {
    fun findByLeague(league: League): List<LeagueRecurrenceWindow>
}
