package io.olkkani.lolviewback.adapter.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pandascore-api")
data class PandaScoreProperties(
    val token: String,
    val url: Url,
) {
    data class Url(
        val brackets: String,
    )
}
