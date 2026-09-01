package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.dao.ClubProfileRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.ClubProfile
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.LogoBackdrop
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

@Testcontainers
@DataJpaTest
class ClubProfileRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var clubProfileRepository: ClubProfileRepository

    @Autowired
    lateinit var entityManager: EntityManager

    private fun clubProfile(logoBackdrop: LogoBackdrop? = null) = ClubProfile(
        clubName = "T1",
        abbreviation = "T1",
        logoUrl = "https://example.com/t1.png",
        effectiveFrom = LocalDate.of(2020, 1, 1),
        effectiveTo = LocalDate.of(2099, 1, 1),
        logoBackdrop = logoBackdrop,
    )

    @Test
    fun `logoBackdrop persists and reads back as the same enum constant`() {
        val saved = clubProfileRepository.save(clubProfile(LogoBackdrop.ANY))
        clubProfileRepository.flush()
        entityManager.clear()

        val found = clubProfileRepository.findById(saved.id!!).get()

        assertEquals(LogoBackdrop.ANY, found.logoBackdrop)
    }

    @Test
    fun `logoBackdrop is stored as its string name`() {
        val saved = clubProfileRepository.save(clubProfile(LogoBackdrop.DARK))
        clubProfileRepository.flush()
        entityManager.clear()

        val query: Query = entityManager.createNativeQuery(
            "SELECT logo_backdrop FROM club_profiles WHERE id = :id",
        )
        query.setParameter("id", saved.id)

        assertEquals("DARK", query.singleResult)
    }

    @Test
    fun `logoBackdrop round-trips as null when uncurated`() {
        val saved = clubProfileRepository.save(clubProfile(null))
        clubProfileRepository.flush()
        entityManager.clear()

        val found = clubProfileRepository.findById(saved.id!!).get()

        assertNull(found.logoBackdrop)
    }
}
