package io.olkkani.lolviewback.application.auth

import io.olkkani.lolviewback.adapter.outbound.persistence.RefreshTokenRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.UserRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers
@DataJpaTest
@Import(RefreshTokenService::class)
@TestPropertySource(properties = ["jwt.refresh-expiration-days=14", "jwt.refresh-grace-period-seconds=20"])
// @DataJpaTest wraps each test in a single rolled-back transaction by default. That transaction
// would own the connection the test method uses to set up fixtures, making rows invisible to the
// separate connections the two worker threads below acquire to exercise real row-level locking.
// NOT_SUPPORTED suspends that test-managed transaction so setup is committed and the worker
// threads see it, and each call into a @Transactional service method still gets its own real
// transaction.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshTokenServiceIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var refreshTokenService: RefreshTokenService

    @Test
    fun `two concurrent rotate calls for the same token do not both succeed as a fresh rotation`() {
        val user = userRepository.save(User(id = 100L))
        val rawToken = refreshTokenService.issue(user.id)

        val executor = Executors.newFixedThreadPool(2)
        val readyLatch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)
        val results = mutableListOf<RotateResult>()

        val tasks = List(2) {
            executor.submit {
                readyLatch.countDown()
                startLatch.await()
                val result = refreshTokenService.rotate(rawToken)
                synchronized(results) { results.add(result) }
            }
        }

        readyLatch.await(5, TimeUnit.SECONDS)
        startLatch.countDown()
        tasks.forEach { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        val rotatedCount = results.count { it is RotateResult.Rotated }
        val graceReuseCount = results.count { it is RotateResult.GracePeriodReuse }

        assertEquals(2, results.size)
        assertEquals(1, rotatedCount)
        assertEquals(1, graceReuseCount)
        assertTrue(results.none { it is RotateResult.TheftDetected })
    }
}
