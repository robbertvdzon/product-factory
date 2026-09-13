package nl.vdzon.productfactory.auth

import nl.vdzon.productfactory.api.advisor.UserDetails
import nl.vdzon.productfactory.api.advisor.UserId
import nl.vdzon.productfactory.api.advisor.ProductMembershipRole
import nl.vdzon.productfactory.api.shared.ProductId
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Service
class ProductAuthorizationService(
    private val users: UserIdentityRepository,
    @Value("\${PF_AUTH_REQUIRED:false}") private val authRequired: Boolean,
) {
    fun current(authentication: Authentication? = SecurityContextHolder.getContext().authentication): UserDetails? {
        if (!authRequired) return null
        val session = authentication?.principal as? ResolvedSession ?: return null
        return users.get(UserId(session.userId))
    }

    fun currentUserId(authentication: Authentication? = SecurityContextHolder.getContext().authentication): UserId =
        current(authentication)?.id ?: users.resolveOrCreate("local@productfactory.invalid", true).id

    fun isFactoryOwner(authentication: Authentication? = SecurityContextHolder.getContext().authentication): Boolean =
        !authRequired || (current(authentication)?.actingRole?.name == "FACTORY_OWNER" && current(authentication)?.effectiveGlobalRoles?.any { it.name == "FACTORY_OWNER" } == true)

    fun canReadProduct(productId: ProductId, authentication: Authentication? = SecurityContextHolder.getContext().authentication): Boolean {
        if (isFactoryOwner(authentication)) return true
        return current(authentication)?.memberships?.any {
            it.productId == productId && it.status.name == "ACTIVE" && it.role.name == current(authentication)?.actingRole?.name
        } == true
    }

    fun canActOnProduct(productId: ProductId, authentication: Authentication? = SecurityContextHolder.getContext().authentication): Boolean =
        canReadProduct(productId, authentication)

    fun requireProduct(productId: ProductId, authentication: Authentication? = SecurityContextHolder.getContext().authentication) {
        if (!canReadProduct(productId, authentication)) throw AccessDeniedException("Geen toegang tot dit product.")
    }

    fun requireRole(productId: ProductId, role: ProductMembershipRole, authentication: Authentication? = SecurityContextHolder.getContext().authentication) {
        if (!authRequired) return
        val user = current(authentication)
        if (user == null || !user.active || user.actingRole.name != role.name || user.memberships.none {
            it.productId == productId && it.role == role && it.status.name == "ACTIVE"
        }) throw AccessDeniedException("Een actief ${role.name}-lidmaatschap in deze rol is vereist.")
    }

    fun requireFactoryOwner(authentication: Authentication? = SecurityContextHolder.getContext().authentication) {
        if (!isFactoryOwner(authentication)) throw AccessDeniedException("Factory owner-rechten zijn vereist.")
    }
}

@Service
class ProductAuthorizationInterceptor(
    private val authorization: ProductAuthorizationService,
    private val jdbc: JdbcTemplate,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val path = request.requestURI
        if (!path.startsWith("/api/") || path.startsWith("/api/auth/") || path.startsWith("/api/test-control/") ||
            path == "/api/foundation" || path == "/api/foundation/implementations" || path == "/api/version") return true

        if (requiresFactoryOwner(request.method, path)) {
            authorization.requireFactoryOwner()
            return true
        }
        resolveProductId(path)?.let { authorization.requireProduct(ProductId(it)) }
        return true
    }

    private fun requiresFactoryOwner(method: String, path: String): Boolean {
        if (path.startsWith("/api/admin/") || path.startsWith("/api/operations/") || path.startsWith("/api/ai/") ||
            path.startsWith("/api/foundation/schedules")) return true
        if (method == "POST" && path == "/api/products") return true
        if (method != "GET" && Regex("^/api/products/[^/]+/(assignment|test-configuration|status|dispatching|epic-approval-mode|schedules)(/.*)?$").matches(path)) return true
        if (method != "GET" && (path.contains("/sessions/run") || path.contains("agent-environment-keys"))) return true
        if (method != "GET" && (path.startsWith("/api/planning/") || path.startsWith("/api/quality/") ||
                path.startsWith("/api/dispatcher/") || Regex("^/api/stories/[^/]+/(developed|cancelled)$").matches(path) ||
                Regex("^/api/epics/[^/]+/(claim|approve|request-refinement|active|ready-for-verification|verification|withdraw|cancel)$").matches(path) ||
                Regex("^/api/products/[^/]+/planning/(replan|reservations|epics/[^/]+/reprioritize)$").matches(path))) return true
        return false
    }

    private fun resolveProductId(path: String): String? {
        Regex("^/api/products/([^/]+)").find(path)?.groupValues?.get(1)?.let { candidate ->
            if (candidate !in setOf("signals", "questions", "meetings")) return candidate
        }
        val lookups = listOf(
            Regex("^/api/epics/([^/]+)") to Pair("pf_epic", "id"),
            Regex("^/api/stories/([^/]+)") to Pair("pf_story", "id"),
            Regex("^/api/bugs/([^/]+)") to Pair("pf_bug", "id"),
            Regex("^/api/products/signals/([^/]+)") to Pair("pf_user_signal", "signal_id"),
            Regex("^/api/products/questions/([^/]+)") to Pair("pf_stakeholder_question", "question_id"),
            Regex("^/api/products/meetings/([^/]+)") to Pair("pf_meeting", "meeting_id"),
            Regex("^/api/conversations/([^/]+)") to Pair("pf_product_conversation", "conversation_id"),
            Regex("^/api/product-requests/([^/]+)") to Pair("pf_product_request", "request_id"),
            Regex("^/api/design/sessions/([^/]+)") to Pair("pf_design_process_session", "id"),
            Regex("^/api/planning/sessions/([^/]+)") to Pair("pf_planning_process_session", "id"),
            Regex("^/api/quality/sessions/([^/]+)") to Pair("pf_quality_process_session", "id"),
            Regex("^/api/quality/work-items/([^/]+)") to Pair("pf_quality_work_item", "id"),
            Regex("^/api/planning/reservations/([^/]+)") to Pair("pf_story_dispatch_reservation", "id"),
            Regex("^/api/dispatcher/sessions/([^/]+)") to Pair("pf_dispatcher_process_session", "id"),
        )
        lookups.forEach { (pattern, tableAndColumn) ->
            val id = pattern.find(path)?.groupValues?.get(1) ?: return@forEach
            return jdbc.query("SELECT product_id FROM ${tableAndColumn.first} WHERE ${tableAndColumn.second}=?", { rs, _ -> rs.getString(1) }, id).singleOrNull()
        }
        return null
    }
}

@org.springframework.context.annotation.Configuration
class ProductAuthorizationWebConfiguration(
    private val interceptor: ProductAuthorizationInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor)
    }
}
