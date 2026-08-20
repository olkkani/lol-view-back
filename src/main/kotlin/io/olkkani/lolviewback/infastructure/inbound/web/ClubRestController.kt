package io.olkkani.lolviewback.infastructure.inbound.web

import io.olkkani.lolviewback.domain.match.ClubService
import io.olkkani.lolviewback.infastructure.inbound.web.dto.ClubResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/clubs")
class ClubRestController(
    private val clubService: ClubService,
) {
    @GetMapping
    fun getClubs(): List<ClubResponse> {
        val userId = SecurityContextHolder.getContext().authentication!!.principal as String
        return clubService.getClubs(userId.toLong())
    }
}