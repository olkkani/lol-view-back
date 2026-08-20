package io.olkkani.lolviewback.domain.auth

sealed class ResolveResult {
    data class LoggedIn(val userId: Long) : ResolveResult()
    data class NewUser(val userId: Long) : ResolveResult()
    data class Linked(val userId: Long) : ResolveResult()
    object AlreadyLinkedElsewhere : ResolveResult()
}
