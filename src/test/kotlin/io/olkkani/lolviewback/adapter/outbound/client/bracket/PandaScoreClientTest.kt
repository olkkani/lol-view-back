package io.olkkani.lolviewback.adapter.outbound.client.bracket

import io.olkkani.lolviewback.adapter.config.PandaScoreProperties
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import kotlin.test.assertEquals

class PandaScoreClientTest {
    private var server: DisposableServer? = null

    @AfterEach
    fun tearDown() {
        server?.disposeNow()
    }

    @Test
    fun `fetches and deserializes a bare match array from the real response shape`() {
        val body =
            """
            [
              {
                "id": 1642162,
                "name": "Lower bracket round 1: BFX vs DK",
                "status": "finished",
                "slug": "2026-09-03-572d56d4",
                "winner_id": 132531,
                "winner_type": "Team",
                "tournament_id": 21722,
                "begin_at": "2026-09-03T08:02:42Z",
                "end_at": "2026-09-03T12:24:59Z",
                "scheduled_at": "2026-09-03T08:00:00Z",
                "previous_matches": [
                  {"type": "loser", "match_id": 1642153},
                  {"type": "loser", "match_id": 1642154}
                ],
                "opponents": [
                  {"type": "Team", "opponent": {"id": 134115, "name": "BNK FEARX"}},
                  {"type": "Team", "opponent": {"id": 132531, "name": "Dplus KIA"}}
                ],
                "results": [
                  {"team_id": 132531, "score": 3},
                  {"team_id": 134115, "score": 2}
                ]
              }
            ]
            """.trimIndent()

        server =
            HttpServer.create()
                .host("localhost")
                .route { routes ->
                    routes.get("/tournaments/21722/brackets") { _, response ->
                        response
                            .header("Content-Type", "application/json")
                            .sendString(Mono.just(body))
                    }
                }
                .bindNow()

        val client =
            PandaScoreClient(
                properties =
                    PandaScoreProperties(
                        token = "test-token",
                        url = PandaScoreProperties.Url(
                            brackets = "http://localhost:${server!!.port()}/tournaments/",
                        ),
                    ),
                webClientBuilder = WebClient.builder(),
            )

        val result = runBlocking { client.fetchBrackets("21722") }

        assertEquals(1, result.size)
        assertEquals(1642162L, result[0].id)
        assertEquals("Lower bracket round 1: BFX vs DK", result[0].name)
        assertEquals("finished", result[0].status)
        assertEquals(132531L, result[0].winnerId)
    }

    @Test
    fun `sends the token as an Authorization Bearer header, not a query parameter`() {
        var capturedAuthHeader: String? = null

        server =
            HttpServer.create()
                .host("localhost")
                .route { routes ->
                    routes.get("/tournaments/21722/brackets") { request, response ->
                        capturedAuthHeader = request.requestHeaders().get("Authorization")
                        response
                            .header("Content-Type", "application/json")
                            .sendString(Mono.just("[]"))
                    }
                }
                .bindNow()

        val client =
            PandaScoreClient(
                properties =
                    PandaScoreProperties(
                        token = "secret-token-value",
                        url = PandaScoreProperties.Url(
                            brackets = "http://localhost:${server!!.port()}/tournaments/",
                        ),
                    ),
                webClientBuilder = WebClient.builder(),
            )

        runBlocking { client.fetchBrackets("21722") }

        assertEquals("Bearer secret-token-value", capturedAuthHeader)
    }

    @Test
    fun `retries when the server resets the connection before responding`() {
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
        val body = "[]"

        server =
            HttpServer.create()
                .host("localhost")
                .route { routes ->
                    routes.get("/tournaments/21722/brackets") { _, response ->
                        if (attempts.getAndIncrement() == 0) {
                            response
                                .withConnection { connection ->
                                    val channel = connection.channel() as io.netty.channel.socket.SocketChannel
                                    channel.config().setOption(io.netty.channel.ChannelOption.SO_LINGER, 0)
                                    channel.close()
                                }.then()
                        } else {
                            response
                                .header("Content-Type", "application/json")
                                .sendString(Mono.just(body))
                        }
                    }
                }
                .bindNow()

        val client =
            PandaScoreClient(
                properties =
                    PandaScoreProperties(
                        token = "test-token",
                        url = PandaScoreProperties.Url(
                            brackets = "http://localhost:${server!!.port()}/tournaments/",
                        ),
                    ),
                webClientBuilder = WebClient.builder(),
            )

        val result = runBlocking { client.fetchBrackets("21722") }

        assertEquals(emptyList(), result)
        assertEquals(2, attempts.get(), "expected exactly one retry after the reset attempt")
    }
}
