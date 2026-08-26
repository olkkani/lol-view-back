package io.olkkani.lolviewback.application.auth

class IdentityAlreadyLinkedException(
    message: String = "This identity is already linked to a different account",
) : RuntimeException(message)
