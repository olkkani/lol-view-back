package io.olkkani.lolviewback.adapter.inbound.web

import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueRequest
import io.olkkani.lolviewback.adapter.inbound.web.dto.LeagueResponse
import io.olkkani.lolviewback.application.service.LeagueService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/leagues")
@PreAuthorize("hasRole('ADMIN')")
class LeagueRestController(
    private val leagueService: LeagueService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createLeague(
        @RequestBody request: LeagueRequest,
    ): LeagueResponse = leagueService.create(request)

    @GetMapping
    fun getLeagues(): List<LeagueResponse> = leagueService.getAll()

    @GetMapping("/{id}")
    fun getLeague(
        @PathVariable id: Long,
    ): LeagueResponse = leagueService.getById(id)

    @PutMapping("/{id}")
    fun updateLeague(
        @PathVariable id: Long,
        @RequestBody request: LeagueRequest,
    ): LeagueResponse = leagueService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLeague(
        @PathVariable id: Long,
    ) = leagueService.delete(id)
}
