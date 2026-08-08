package io.olkkani.lolviewback.infastructure.inbound.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController


@RestController
class MatchRestController {


    @GetMapping("/")
    fun getMatches(): String{
        return "hello"
    }
}