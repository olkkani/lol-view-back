package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExternalIdTest {
    @Test
    fun `Provider enum has exactly LOLESPORTS and PANDASCORE`() {
        assertEquals(setOf("LOLESPORTS", "PANDASCORE"), Provider.entries.map { it.name }.toSet())
    }
}
