package io.olkkani.lolviewback.adapter.inbound.web.dto

data class MeResponse(
    val isAdmin: Boolean,
    val identities: List<UserIdentityResponse>,
)
