package io.olkkani.lolviewback

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class LolViewBackApplication

fun main(args: Array<String>) {
    runApplication<LolViewBackApplication>(*args)
}
