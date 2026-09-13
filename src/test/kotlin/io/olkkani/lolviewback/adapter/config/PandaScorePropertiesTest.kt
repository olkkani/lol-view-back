package io.olkkani.lolviewback.adapter.config

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertEquals

class PandaScorePropertiesTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PandaScoreConfig::class.java)

    @Test
    fun `binds token and brackets url from configuration`() {
        contextRunner
            .withPropertyValues(
                "pandascore-api.token=test-token",
                "pandascore-api.url.brackets=http://localhost:9999/tournaments/",
            )
            .run { context ->
                val properties = context.getBean(PandaScoreProperties::class.java)
                assertEquals("test-token", properties.token)
                assertEquals("http://localhost:9999/tournaments/", properties.url.brackets)
            }
    }
}
