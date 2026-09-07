package io.olkkani.lolviewback.adapter.outbound.client.sync

import io.olkkani.lolviewback.adapter.config.LolApiProperties
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class LolEsportsApiClientTest {
    private var server: DisposableServer? = null

    @AfterEach
    fun tearDown() {
        server?.disposeNow()
    }

    @Test
    fun `retries when the server resets the connection before responding`() {
        val attempts = AtomicInteger(0)
        val tournamentBody =
            """
            {"data":{"leagues":[{"tournaments":[]}]}}
            """.trimIndent()

        server =
            HttpServer.create()
                .host("localhost")
                .route { routes ->
                    routes.get("/persisted/gw/getTournamentsForLeague") { _, response ->
                        if (attempts.getAndIncrement() == 0) {
                            // Abruptly reset the TCP connection instead of responding,
                            // reproducing the "Connection reset" the server can send back.
                            response
                                .withConnection { connection ->
                                    val channel = connection.channel() as io.netty.channel.socket.SocketChannel
                                    channel.config().setOption(io.netty.channel.ChannelOption.SO_LINGER, 0)
                                    channel.close()
                                }.then()
                        } else {
                            response
                                .header("Content-Type", "application/json")
                                .sendString(reactor.core.publisher.Mono.just(tournamentBody))
                        }
                    }
                }
                .bindNow()

        val client =
            LolEsportsApiClient(
                properties =
                    LolApiProperties(
                        key = "test-key",
                        url =
                            LolApiProperties.Url(
                                tournament = "http://localhost:${server!!.port()}/persisted/gw/getTournamentsForLeague?leagueId=",
                                match = "http://localhost:${server!!.port()}/persisted/gw/getSchedule?leagueId=",
                                sets = "http://localhost:${server!!.port()}/persisted/gw/getEventDetails?matchId=",
                            ),
                    ),
                webClientBuilder = WebClient.builder(),
            )

        val result = runBlocking { client.fetchTournaments("98767991302996019") }

        assertEquals(emptyList(), result)
        assertEquals(2, attempts.get(), "expected exactly one retry after the reset attempt")
    }
}
