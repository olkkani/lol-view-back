package io.olkkani.lolviewback.infastructure.inbound.web.dto

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

enum class MatchRange {
    YESTERDAY,
    TODAY,
    UPCOMING,
    ;

    fun toDateRange(today: LocalDate): Pair<ZonedDateTime, ZonedDateTime> {
        val todayStart = today.atStartOfDay(KST)
        return when (this) {
            YESTERDAY -> todayStart.minusDays(1) to todayStart
            TODAY -> todayStart to todayStart.plusDays(1)
            UPCOMING -> todayStart.plusDays(1) to todayStart.plusDays(8)
        }
    }

    companion object {
        fun from(value: String): MatchRange {
            return entries.firstOrNull { it.name == value.uppercase() && it.name.lowercase() == value }
                ?: throw IllegalArgumentException("Unknown range: $value")
        }
    }
}
