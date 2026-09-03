package io.olkkani.lolviewback.adapter.inbound.security

import io.olkkani.lolviewback.application.auth.JwtParseResult
import io.olkkani.lolviewback.application.auth.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractToken(request)
        if (token != null) {
            when (val result = jwtService.parseResult(token)) {
                is JwtParseResult.Valid -> {
                    val authentication = UsernamePasswordAuthenticationToken(result.userId.toString(), null, emptyList())
                    SecurityContextHolder.getContext().authentication = authentication
                }
                else -> request.setAttribute("jwt.parse.result", result)
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            return header.removePrefix("Bearer ")
        }
        return request.cookies?.firstOrNull { it.name == "access_token" }?.value
    }
}
