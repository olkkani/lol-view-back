package io.olkkani.lolviewback.adapter.outbound.persistence.dao

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchSet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MatchSetRepository : JpaRepository<MatchSet, Long> {
    fun save(matchSet: MatchSet): MatchSet
}