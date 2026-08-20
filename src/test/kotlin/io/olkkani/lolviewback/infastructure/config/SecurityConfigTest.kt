package io.olkkani.lolviewback.infastructure.config

import io.olkkani.lolviewback.domain.auth.JwtService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtService: JwtService

    @Test
    fun `an unauthenticated request to a protected endpoint is rejected with 401`() {
        mockMvc.get("/clubs")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `oauth2 login entry point is reachable without authentication`() {
        mockMvc.get("/oauth2/authorization/google")
            .andExpect { status { is3xxRedirection() } }
    }

    @Test
    fun `GET matches is reachable without authentication`() {
        // No range param is supplied, so this returns 400 (bad request), not 200 -
        // the point here is only proving it's not blocked with a 401.
        mockMvc.get("/matches")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a request with a valid bearer token is authenticated`() {
        val token = jwtService.issueToken(userId = 1L)

        mockMvc.get("/clubs") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
        }
    }
}
