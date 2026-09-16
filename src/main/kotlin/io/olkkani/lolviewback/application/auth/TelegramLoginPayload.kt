package io.olkkani.lolviewback.application.auth

data class TelegramLoginPayload(
    val id: Long,
    val firstName: String,
    val lastName: String?,
    val username: String?,
    val photoUrl: String?,
    val authDate: Long,
    val hash: String,
)
