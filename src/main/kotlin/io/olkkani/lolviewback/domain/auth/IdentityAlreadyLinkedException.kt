package io.olkkani.lolviewback.domain.auth

class IdentityAlreadyLinkedException(
    message: String = "This identity is already linked to a different account",
) : RuntimeException(message)
