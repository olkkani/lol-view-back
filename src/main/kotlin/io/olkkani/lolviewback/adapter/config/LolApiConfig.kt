package io.olkkani.lolviewback.adapter.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(LolApiProperties::class)
class LolApiConfig
