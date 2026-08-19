package io.olkkani.lolviewback.domain.match

import io.olkkani.lolviewback.infastructure.inbound.web.dto.ClubResponse
import io.olkkani.lolviewback.infastructure.inbound.web.dto.toResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.ClubProfileJooqRepository
import org.springframework.stereotype.Service

@Service
class ClubService(
    private val clubProfileJooqRepository: ClubProfileJooqRepository,
) {
    fun getClubs(userId: Long): List<ClubResponse> =
        clubProfileJooqRepository.findAllActive(userId).map { it.toResponse() }
}
