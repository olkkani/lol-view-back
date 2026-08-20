package io.olkkani.lolviewback.infastructure.inbound.web.dto

data class UserIdentityResponse(
    val provider: String,
    val providerUserId: String,
)
