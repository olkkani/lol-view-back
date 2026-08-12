package io.olkkani.lolviewback.infastructure.inbound.web.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MatchRangeTest {

    private val kst = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 8, 12)

    @Test
    fun `from parses valid values case-sensitively`() {
        assertEquals(MatchRange.YESTERDAY, MatchRange.from("yesterday"))
        assertEquals(MatchRange.TODAY, MatchRange.from("today"))
        assertEquals(MatchRange.UPCOMING, MatchRange.from("upcoming"))
    }

    @Test
    fun `from throws on invalid value`() {
        assertThrows(InvalidMatchRangeException::class.java) {
            MatchRange.from("tomorrow")
        }
    }

    @Test
    fun `from throws on empty value`() {
        assertThrows(InvalidMatchRangeException::class.java) {
            MatchRange.from("")
        }
    }

    @Test
    fun `from throws on mixed-case value`() {
        assertThrows(InvalidMatchRangeException::class.java) {
            MatchRange.from("Today")
        }
        assertThrows(InvalidMatchRangeException::class.java) {
            MatchRange.from("TODAY")
        }
    }

    @Test
    fun `yesterday range is the day before today in KST`() {
        val (start, end) = MatchRange.YESTERDAY.toDateRange(today)

        assertEquals(ZonedDateTime.of(2026, 8, 11, 0, 0, 0, 0, kst), start)
        assertEquals(ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst), end)
    }

    @Test
    fun `today range is today 00_00 to tomorrow 00_00 in KST`() {
        val (start, end) = MatchRange.TODAY.toDateRange(today)

        assertEquals(ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst), start)
        assertEquals(ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst), end)
    }

    @Test
    fun `upcoming range is tomorrow through 7 days later in KST`() {
        val (start, end) = MatchRange.UPCOMING.toDateRange(today)

        assertEquals(ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst), start)
        assertEquals(ZonedDateTime.of(2026, 8, 20, 0, 0, 0, 0, kst), end)
    }
}
