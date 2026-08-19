package io.olkkani.lolviewback.infastructure.inbound.web

import io.olkkani.lolviewback.domain.match.ClubService
import io.olkkani.lolviewback.infastructure.inbound.web.dto.ClubResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/clubs")
class ClubRestController(
    private val clubService: ClubService,
) {
    @GetMapping
    fun getClubs(
        @RequestParam userId: Long,
    ): List<ClubResponse> = clubService.getClubs(userId)
}