package io.olkkani.lolviewback.application.outbound

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Match

interface MatchRepositoryPort {
    fun findAllInProgress(): List<Match>
}