package io.olkkani.lolviewback.adapter.outbound.persistence.dao

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubProfile
import org.springframework.data.jpa.repository.JpaRepository


interface ClubProfileRepository : JpaRepository<ClubProfile, Long>{
    fun findByAbbreviation(abbreviation: String): ClubProfile
}
