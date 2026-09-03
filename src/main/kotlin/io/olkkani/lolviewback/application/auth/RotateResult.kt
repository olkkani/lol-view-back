package io.olkkani.lolviewback.application.auth

sealed class RotateResult {
    data class Rotated(val userId: Long, val newRawToken: String) : RotateResult()
    data class GracePeriodReuse(val userId: Long, val reissuedRawToken: String) : RotateResult()
    object TheftDetected : RotateResult()
    object NotFound : RotateResult()
}
