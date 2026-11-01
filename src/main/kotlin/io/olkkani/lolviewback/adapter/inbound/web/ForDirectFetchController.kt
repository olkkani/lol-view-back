package io.olkkani.lolviewback.adapter.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.olkkani.lolviewback.application.service.TournamentSyncService
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

//@Profile("dev")
@RestController
@RequestMapping("/test")
class ForDirectFetchController(
    private val tournamentSyncService: TournamentSyncService,
) {

    private val log = KotlinLogging.logger {}

    @RequestMapping("/tournaments")
    fun fetchTournamnets() {
        try {
            runBlocking {
                tournamentSyncService.syncNewTournaments()
            }
            log.info { "Finished tournament sync" }
        } catch (e: Exception) {
            log.error(e) { "Failed to sync tournaments" }
        }
    }
}