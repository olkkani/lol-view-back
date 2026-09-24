package io.olkkani.lolviewback.adapter.inbound.web

import com.fasterxml.jackson.databind.ObjectMapper
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Role
import io.olkkani.lolviewback.application.auth.JwtService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * Verifies the ADMIN gate on LeagueRestController end-to-end through real Spring Security
 * filters and @PreAuthorize method security - a plain mockk controller test bypasses the
 * AOP proxy that enforces @PreAuthorize, so this is the only place that check is exercised.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeagueRestControllerSecurityTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtService: JwtService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun tokenFor(role: Role) = jwtService.issueToken(userId = 1L, role = role)

    private val createBody = """{"leagueName":"LCK","logoUrl":"https://example.com/lck.png","logoBackdrop":"DARK"}"""

    @Test
    fun `unauthenticated request to create a league is rejected with 401`() {
        mockMvc
            .post("/leagues") {
                contentType = MediaType.APPLICATION_JSON
                content = createBody
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `USER role is rejected with 403 on create`() {
        mockMvc
            .post("/leagues") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenFor(Role.USER)}")
                contentType = MediaType.APPLICATION_JSON
                content = createBody
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `USER role is rejected with 403 on list`() {
        mockMvc
            .get("/leagues") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenFor(Role.USER)}")
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `USER role is rejected with 403 on update`() {
        mockMvc
            .put("/leagues/1") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenFor(Role.USER)}")
                contentType = MediaType.APPLICATION_JSON
                content = createBody
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `USER role is rejected with 403 on delete`() {
        mockMvc
            .delete("/leagues/1") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenFor(Role.USER)}")
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `ADMIN role can create and then fetch a league`() {
        val adminToken = tokenFor(Role.ADMIN)

        val createResult =
            mockMvc
                .post("/leagues") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = createBody
                }.andExpect { status { isCreated() } }
                .andReturn()

        val createdId = objectMapper.readTree(createResult.response.contentAsString).get("id").asLong()

        mockMvc
            .get("/leagues/$createdId") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
            }.andExpect {
                status { isOk() }
                jsonPath("$.leagueName") { value("LCK") }
            }
    }

    @Test
    fun `ADMIN role gets 404 for a league that does not exist`() {
        mockMvc
            .get("/leagues/999999999") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenFor(Role.ADMIN)}")
            }.andExpect { status { isNotFound() } }
    }
}
