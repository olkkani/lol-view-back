package io.olkkani.lolviewback.adapter.outbound.client.sync

import io.olkkani.lolviewback.adapter.config.LolApiProperties
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchApiResponse
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchApiResponseWrapper
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchScheduleEvent
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchSetApiResponse
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.MatchSetApiResponseWrapper
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.TournamentApiResponse
import io.olkkani.lolviewback.adapter.outbound.client.sync.dto.TournamentApiResponseWrapper
import io.olkkani.lolviewback.application.outbound.LolApiClientPort
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.netty.http.client.HttpClient
import java.time.Duration

/**
 * WebClient-based client for esports-api.lolesports.com's tournament/schedule endpoints.
 *
 * URI construction: `properties.url.tournament` / `properties.url.match` already end in
 * `...&leagueId=` (confirmed by Task 2's spike, which built its curl URLs the same way —
 * see task-2-brief.md's `"${LOL_API_URL_TOURNAMENT}98767991302996019"` example). So the
 * final URI is built by direct string concatenation of the configured URL and the
 * leagueApiId — no extra `?leagueId=` prefix is added here, since the query param name
 * and `=` are already baked into the configured property value.
 */
@Component
class LolEsportsApiClient(
    private val properties: LolApiProperties,
    webClientBuilder: WebClient.Builder,
): LolApiClientPort {
    private val httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(10))
    private val webClient = webClientBuilder.clientConnector(ReactorClientHttpConnector(httpClient)).build()

    override suspend fun fetchTournaments(leagueApiId: String): List<TournamentApiResponse> {
        val wrapper =
            webClient
                .get()
                .uri("${properties.url.tournament}$leagueApiId")
                .header("x-api-key", properties.key)
                .retrieve()
                .awaitBody<TournamentApiResponseWrapper>()
        return wrapper.data.leagues.flatMap { it.tournaments }
    }

    override suspend fun fetchMatchesForLeague(leagueApiId: String): List<MatchApiResponse> {
        val wrapper =
            webClient
                .get()
                .uri("${properties.url.match}$leagueApiId")
                .header("x-api-key", properties.key)
                .retrieve()
                .awaitBody<MatchApiResponseWrapper>()
        return wrapper.data.schedule.events
            .map { MatchApiResponse.from(it) }
    }

    /**
     * esports-api does not expose a verified separate matchId-detail endpoint (Task 2's
     * spike only exercised the League-wide `getTournamentsForLeague`/`getSchedule`
     * endpoints, and did not test a per-match detail URL). Rather than guess at an
     * unverified endpoint pattern, this reuses [fetchMatchesForLeague] and filters by
     * [matchApiId] client-side. Returns null when not found — "not found" is a valid
     * outcome here, not an error.
     */
    override suspend fun fetchMatchDetail(
        matchApiId: String,
        leagueApiId: String,
    ): MatchApiResponse? =
        fetchMatchesForLeague(leagueApiId)
            .find { it.apiId == matchApiId }

    override suspend fun fetchMatches(leagueApiId: String): List<MatchScheduleEvent> {
        val wrapper =
            webClient
                .get()
                .uri("${properties.url.match}$leagueApiId")
                .header("x-api-key", properties.key)
                .retrieve()
                .awaitBody<MatchApiResponseWrapper>()
        return wrapper.data.schedule.events
    }


    override suspend fun fetchMatchSet(matchApiId: String): MatchSetApiResponse? {
            val wrapper =
                webClient
                    .get()
                    .uri("${properties.url.sets}$matchApiId")
                    .header("x-api-key", properties.key)
                    .retrieve()
                    .awaitBody<MatchSetApiResponseWrapper>()
            return wrapper.data.event
    }
}


