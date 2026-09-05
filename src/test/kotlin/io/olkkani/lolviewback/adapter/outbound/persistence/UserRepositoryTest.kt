package io.olkkani.lolviewback.adapter.outbound.persistence

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Role
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DataJpaTest
class UserRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `a newly constructed user defaults to USER role`() {
        val saved = userRepository.save(User())

        val reloaded = userRepository.findById(saved.id).orElseThrow()

        assertEquals(Role.USER, reloaded.role)
    }

    @Test
    fun `role persists and reloads as ADMIN`() {
        val user = User(role = Role.ADMIN)
        val saved = userRepository.save(user)

        val reloaded = userRepository.findById(saved.id).orElseThrow()

        assertEquals(Role.ADMIN, reloaded.role)
    }
}
