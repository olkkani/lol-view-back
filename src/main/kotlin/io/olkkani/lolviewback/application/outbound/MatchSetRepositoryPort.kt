package io.olkkani.lolviewback.application.outbound

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchSet

interface MatchSetRepositoryPort {
    fun findAllInProgress(): List<MatchSet>
    fun save(matchSet: MatchSet)
    fun findAllGroupByMatchIds(matchIds: List<Long>): Map<Long, List<MatchSet>>
}