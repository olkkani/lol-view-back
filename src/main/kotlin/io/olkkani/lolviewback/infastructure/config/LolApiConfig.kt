package io.olkkani.lolviewback.infastructure.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(LolApiProperties::class)
class LolApiConfig
