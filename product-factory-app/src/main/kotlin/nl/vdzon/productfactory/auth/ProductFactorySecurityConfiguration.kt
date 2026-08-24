package nl.vdzon.productfactory.auth

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
class ProductFactorySecurityConfiguration(
    @Value("\${PF_AUTH_REQUIRED:false}") private val authRequired: Boolean,
    @Value("\${PF_PUBLIC_FRONTEND_URL:http://localhost:8082}") private val frontendUrl: String,
    private val sessionServiceProvider: ObjectProvider<ProductFactorySessionService>,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .exceptionHandling {
                it.authenticationEntryPoint(jsonAuthenticationEntryPoint())
                it.accessDeniedHandler(jsonAccessDeniedHandler())
            }
            .authorizeHttpRequests { authorization ->
                if (!authRequired) {
                    authorization.anyRequest().permitAll()
                } else {
                    authorization
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                            "/api/auth/google",
                            "/api/auth/logout",
                            "/api/auth/session",
                            "/api/foundation",
                            "/api/version",
                            "/actuator/health",
                            "/actuator/health/**",
                            "/error",
                        ).permitAll()
                        .anyRequest().authenticated()
                }
            }

        if (authRequired) {
            val sessionService = sessionServiceProvider.getIfAvailable()
                ?: error("Authenticatie is verplicht, maar de sessieservice ontbreekt.")
            http.addFilterBefore(
                ProductFactorySessionFilter(sessionService, frontendUrl, objectMapper),
                UsernamePasswordAuthenticationFilter::class.java,
            )
        }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf(frontendUrl.removeSuffix("/"))
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Content-Type", ProductFactorySessionService.CSRF_HEADER)
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().also { it.registerCorsConfiguration("/**", configuration) }
    }

    private fun jsonAuthenticationEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { _, response, _ ->
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Login is vereist.")
        }

    private fun jsonAccessDeniedHandler(): AccessDeniedHandler = AccessDeniedHandler { _, response, _ ->
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Deze aanvraag is niet toegestaan.")
    }

    private fun writeError(response: HttpServletResponse, status: Int, code: String, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.writer, AuthenticationError(code, message))
    }
}

private class ProductFactorySessionFilter(
    private val sessionService: ProductFactorySessionService,
    frontendUrl: String,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    private val allowedOrigin = frontendUrl.removeSuffix("/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val session = sessionService.resolve(request)
        if (session != null) {
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken.authenticated(
                session,
                null,
                listOf(SimpleGrantedAuthority("ROLE_STAKEHOLDER")),
            )
        }

        if (request.method in MUTATING_METHODS) {
            val origin = request.getHeader(HttpHeaders.ORIGIN)?.removeSuffix("/")
            if (origin != allowedOrigin) {
                forbidden(response, "De request-origin is niet toegestaan.")
                return
            }
            if (request.requestURI != "/api/auth/google" && session != null && !sessionService.validateCsrf(request, session)) {
                forbidden(response, "CSRF-validatie is mislukt.")
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun forbidden(response: HttpServletResponse, message: String) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.writer, AuthenticationError("FORBIDDEN", message))
    }

    companion object {
        private val MUTATING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}
