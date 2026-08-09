package io.olkkani.lolviewback.infastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "lol-api")
data class LolApiProperties(
    val key: String,
    val url: Url,
) {
    data class Url(
        val league: String = "",
        val tournament: String,
        val match: String,
    )
}
