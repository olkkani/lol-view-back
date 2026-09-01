package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.inbound.web.dto.ClubResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.toResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.ClubProfileJooqRepository
import org.springframework.stereotype.Service

@Service
class ClubService(
    private val clubProfileJooqRepository: ClubProfileJooqRepository,
) {
    fun getClubs(userId: Long): List<ClubResponse> =
        clubProfileJooqRepository.findAllActive(userId).map { it.toResponse() }
}
