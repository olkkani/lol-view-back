package io.olkkani.lolviewback.adapter.config

import io.olkkani.lolviewback.adapter.inbound.security.JwtAuthenticationEntryPoint
import io.olkkani.lolviewback.adapter.inbound.security.JwtAuthenticationFilter
import io.olkkani.lolviewback.adapter.inbound.security.OAuth2FailureHandler
import io.olkkani.lolviewback.adapter.inbound.security.OAuth2SuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val oAuth2SuccessHandler: OAuth2SuccessHandler,
    private val oAuth2FailureHandler: OAuth2FailureHandler,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .headers { header ->
                header.xssProtection { xss ->
                    xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                }
                header.frameOptions { it.deny() }
            }
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSourceLocal()) }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/refresh", "/auth/logout").permitAll()
                    .requestMatchers(HttpMethod.GET, "/matches").permitAll()
                    .requestMatchers("/actuator/health/**").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptionHandling ->
                // Without this, Spring Security's default entry point for an
                // unauthenticated request redirects (302) to the OAuth2 login page
                // whenever oauth2Login() is configured. This API is token-based
                // (JwtAuthenticationFilter), so unauthenticated requests to protected
                // endpoints must return 401, not a login-page redirect.
                exceptionHandling.authenticationEntryPoint(jwtAuthenticationEntryPoint)
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .successHandler(oAuth2SuccessHandler)
                    .failureHandler(oAuth2FailureHandler)
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    fun corsConfigurationSourceLocal(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf("http://localhost:80", "https://gemspi.kro.kr", "http://ngnix:80")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
