package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.ClubProfile
import org.springframework.data.jpa.repository.JpaRepository

interface ClubProfileRepository : JpaRepository<ClubProfile, Long>
