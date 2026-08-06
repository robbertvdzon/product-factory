package nl.vdzon.productfactory.dashboard

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class DashboardCorsConfiguration(
    @Value("\${PF_DASHBOARD_ORIGIN:http://localhost:8082}") private val dashboardOrigin: String,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**").allowedOrigins(dashboardOrigin).allowedMethods("GET", "POST", "OPTIONS").allowedHeaders("Authorization", "Content-Type")
    }
}
