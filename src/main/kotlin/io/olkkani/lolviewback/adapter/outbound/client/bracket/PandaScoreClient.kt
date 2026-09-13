package io.olkkani.lolviewback.adapter.outbound.client.bracket

import io.olkkani.lolviewback.adapter.config.PandaScoreProperties
import io.olkkani.lolviewback.adapter.outbound.client.bracket.dto.PandaScoreMatch
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import reactor.util.retry.Retry
import java.time.Duration

private val RETRY_SPEC: Retry =
    Retry
        .backoff(2, Duration.ofMillis(200))
        .filter { it is WebClientRequestException }

/**
 * WebClient-based client for PandaScore's `/tournaments/{id}/brackets` endpoint.
 * Reuses the same connection-pool/timeout/retry configuration as
 * [io.olkkani.lolviewback.adapter.outbound.client.sync.LolEsportsApiClient].
 *
 * Unlike lolesports's `{"data": {...}}` envelope, PandaScore's response is a
 * bare JSON array, so a [ParameterizedTypeReference] is used to deserialize
 * directly into `List<PandaScoreMatch>` rather than a wrapper class.
 */
@Component
class PandaScoreClient(
    private val properties: PandaScoreProperties,
    webClientBuilder: WebClient.Builder,
) {
    private val connectionProvider =
        ConnectionProvider
            .builder("pandascore-api")
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofMinutes(5))
            .evictInBackground(Duration.ofSeconds(30))
            .build()
    private val httpClient = HttpClient.create(connectionProvider).responseTimeout(Duration.ofSeconds(10))
    private val webClient = webClientBuilder.clientConnector(ReactorClientHttpConnector(httpClient)).build()

    suspend fun fetchBrackets(tournamentId: String): List<PandaScoreMatch> =
        webClient
            .get()
            .uri("${properties.url.brackets}$tournamentId/brackets")
            .header("Authorization", "Bearer ${properties.token}")
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<PandaScoreMatch>>() {})
            .retryWhen(RETRY_SPEC)
            .awaitSingle()
}
