package io.olkkani.lolviewback.adapter.config

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@Testcontainers
@SpringBootTest
@TestPropertySource(properties = [
    "pandascore-api.token=test-token",
    "pandascore-api.url.brackets=http://localhost:9999/tournaments/",
    "lol-api.key=test-key",
    "lol-api.url.tournament=http://localhost:9999/tournament/",
    "lol-api.url.match=http://localhost:9999/match/",
    "lol-api.url.sets=http://localhost:9999/sets/",
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
    "jwt.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=",
    "jwt.access-expiration-minutes=30",
    "jwt.refresh-expiration-days=14",
    "jwt.refresh-grace-period-seconds=20",
    "app.frontend-url=http://localhost:5173",
    "app.cors.allowed-origins=http://localhost:5173",
])
class PandaScorePropertiesTest {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var properties: PandaScoreProperties

    @Test
    fun `binds token and brackets url from configuration`() {
        assertEquals("test-token", properties.token)
        assertEquals("http://localhost:9999/tournaments/", properties.url.brackets)
    }
}
