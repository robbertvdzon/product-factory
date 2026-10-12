package nl.vdzon.productfactory.advisor

import nl.vdzon.productfactory.api.advisor.CreateConversationCommand
import nl.vdzon.productfactory.api.advisor.ProductRequestId
import nl.vdzon.productfactory.api.advisor.UserId
import nl.vdzon.productfactory.api.product.CreateProductCommand
import nl.vdzon.productfactory.api.product.ProductCommandService
import nl.vdzon.productfactory.api.shared.ActorReference
import nl.vdzon.productfactory.api.shared.ActorType
import nl.vdzon.productfactory.api.shared.ProductId
import nl.vdzon.productfactory.auth.UserIdentityRepository
import nl.vdzon.productfactory.design.mvp.ProductDesignMvpService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false", "PF_SOFTWARE_FACTORY_MODE=MOCKED"])
@ActiveProfiles("test")
@Transactional
class DirectedDesignRetryIntegrationTest(
    @Autowired private val advisor: ProductAdvisorApplicationService,
    @Autowired private val products: ProductCommandService,
    @Autowired private val users: UserIdentityRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @MockitoBean
    private lateinit var design: ProductDesignMvpService

    private var productId = ProductId("uninitialized")
    private var ownerId = UserId("uninitialized")

    @BeforeEach
    fun prepare() {
        productId = ProductId("retry-${UUID.randomUUID().toString().take(8)}")
        ownerId = users.resolveOrCreate("owner-${productId.value}@example.test", true).id
        products.createProduct(CreateProductCommand(
            productId, "Directed retry product", actor = SYSTEM, idempotencyKey = "create-${productId.value}",
        ))
    }

    @Test
    fun `in behandeling genomen aanvraag wordt niet iedere schedulerronde opnieuw gerouteerd`() {
        val request = request(status = "APPROVED")

        advisor.routeApprovedRequests()
        val initialVersion = advisor.getRequest(request).version
        advisor.routeApprovedRequests()
        advisor.routeApprovedRequests()

        assertThat(advisor.getRequest(request).version).isEqualTo(initialVersion)
        assertThat(jdbc.queryForObject(
            "SELECT attempt_count FROM pf_product_request_route WHERE request_id=? AND request_version=1",
            Int::class.java, request.value,
        )).isEqualTo(1)
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM pf_design_work_item WHERE request_id=?",
            Int::class.java, request.value,
        )).isEqualTo(1)
    }

    @Test
    fun `tijdelijke blokkade wacht vijf minuten ook als een tweede aanvraag klaarstaat`() {
        val session = session(errorCode = "AI_UNAVAILABLE")
        work(request(), status = "BLOCKED", sessionId = session)
        work(request(), status = "PENDING")

        advisor.routeApprovedRequests()
        advisor.routeApprovedRequests()
        verify(design, never()).runProcessSession(productId)

        jdbc.update("UPDATE pf_design_process_session SET updated_at=? WHERE id=?", Instant.now().minusSeconds(301), session)
        advisor.routeApprovedRequests()

        verify(design, times(1)).runProcessSession(productId)
    }

    @Test
    fun `te grote context wordt niet automatisch herhaald na de afkoelperiode`() {
        val session = session(errorCode = "DESIGN_CONTEXT_TOO_LARGE", updatedAt = Instant.now().minusSeconds(3600))
        work(request(), status = "BLOCKED", sessionId = session)
        work(request(), status = "PENDING")

        advisor.routeApprovedRequests()
        advisor.routeApprovedRequests()

        verify(design, never()).runProcessSession(productId)
    }

    @Test
    fun `open vervolgvraag blijft wachten en laatste antwoord hervat direct dezelfde sessie`() {
        val session = session(errorCode = "WAITING_FOR_USER")
        val request = request()
        val workId = work(request, status = "WAITING_FOR_USER", sessionId = session)
        question(request, session, status = "ANSWERED")
        val openQuestion = question(request, session, status = "OPEN")
        work(request(), status = "PENDING")

        advisor.routeApprovedRequests()

        verify(design, never()).runProcessSession(productId)
        assertThat(workStatus(workId)).isEqualTo("WAITING_FOR_USER")

        jdbc.update("UPDATE pf_stakeholder_question SET status='ANSWERED',answer='Bevestigd',answered_at=? WHERE question_id=?",
            Instant.now(), openQuestion)
        advisor.routeApprovedRequests()

        verify(design, times(1)).runProcessSession(productId)
        assertThat(workStatus(workId)).isEqualTo("BLOCKED")
        assertThat(jdbc.queryForObject("SELECT process_session_id FROM pf_design_work_item WHERE work_item_id=?",
            String::class.java, workId)).isEqualTo(session)
    }

    @Test
    fun `wachtende vraag zonder antwoord wordt ook na vijf minuten niet opnieuw uitgevoerd`() {
        val session = session(errorCode = "WAITING_FOR_USER", updatedAt = Instant.now().minusSeconds(3600))
        val request = request()
        val workId = work(request, status = "WAITING_FOR_USER", sessionId = session)
        question(request, session, status = "OPEN")
        work(request(), status = "PENDING")

        advisor.routeApprovedRequests()

        verify(design, never()).runProcessSession(productId)
        assertThat(workStatus(workId)).isEqualTo("WAITING_FOR_USER")
    }

    @Test
    fun `nieuwe aanvraag zonder actieve ontwerpsessie start gewoon`() {
        work(request(), status = "PENDING")

        advisor.routeApprovedRequests()

        verify(design, times(1)).runProcessSession(productId)
    }

    @ParameterizedTest
    @ValueSource(strings = ["RUNNING", "WAITING_FOR_AI"])
    fun `lopende ontwerpsessie blijft zonder afkoelperiode voortgang controleren`(status: String) {
        val session = session(status = status, errorCode = null)
        work(request(), status = "IN_PROGRESS", sessionId = session)

        advisor.routeApprovedRequests()

        verify(design, times(1)).runProcessSession(productId)
    }

    @ParameterizedTest
    @ValueSource(strings = ["PROPOSED", "CANCELLED", "ROUTED"])
    fun `werk voor niet actieve aanvragen wordt niet hervat`(status: String) {
        work(request(status = status), status = "PENDING")

        advisor.routeApprovedRequests()

        verify(design, never()).runProcessSession(productId)
    }

    @Test
    fun `oude aanvraagversie kan de wachtende huidige versie niet opnieuw starten`() {
        val request = request(version = 2)
        work(request, status = "PENDING", version = 1)
        work(request, status = "WAITING_FOR_USER", version = 2)

        advisor.routeApprovedRequests()

        verify(design, never()).runProcessSession(productId)
    }

    private fun request(status: String = "ROUTING", version: Long = 1): ProductRequestId {
        val id = ProductRequestId(UUID.randomUUID().toString())
        val conversation = advisor.createConversation(CreateConversationCommand(
            productId, "Gerichte aanvraag", ownerId, "conversation-${id.value}",
        ))
        val now = Instant.now()
        jdbc.update(
            """INSERT INTO pf_product_request(request_id,product_id,conversation_id,requested_by,request_type,status,current_version,created_at,updated_at,version)
                VALUES (?,?,?,?,'EPIC_CANDIDATE',?,?,?,?,1)""".trimIndent(),
            id.value, productId.value, conversation.value, ownerId.value, status, version, now, now,
        )
        jdbc.update(
            """INSERT INTO pf_product_request_version(request_id,version,request_type,title,summary,problem,user_impact,current_behavior,
                desired_behavior,evidence_json,git_commit_sha,acceptance_criteria_json,scope_json,boundaries_json,excluded_hotfix_categories_json,created_at)
                VALUES (?,?,'EPIC_CANDIDATE',?,?,?,?,?,?,'[]',?,'[]','[]','[]','[]',?)""".trimIndent(),
            id.value, version, "Verbetering", "Gerichte verbetering", "De route ontbreekt", "Gebruikers lopen vast",
            "Geen overzicht", "Wel een overzicht", "a".repeat(40), now,
        )
        return id
    }

    private fun work(request: ProductRequestId, status: String, sessionId: String? = null, version: Long = 1): String {
        val id = UUID.randomUUID().toString()
        val now = Instant.now()
        jdbc.update(
            """INSERT INTO pf_design_work_item(work_item_id,product_id,request_id,request_version,purpose,status,process_session_id,created_at,updated_at,idempotency_key)
                VALUES (?,?,?,?,'CREATE_EPIC_FROM_PRODUCT_REQUEST',?,?,?,?,?)""".trimIndent(),
            id, productId.value, request.value, version, status, sessionId, now, now, "design-${request.value}-v$version",
        )
        return id
    }

    private fun session(status: String = "BLOCKED", errorCode: String?, updatedAt: Instant = Instant.now()): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO pf_design_process_session(id,product_id,active_product_id,status,implementation_artifact,
                implementation_variant,implementation_version,implementation_revision,inputs_json,memory_version_ids_json,
                ai_task_ids_json,publications_json,error_code,started_at,updated_at)
                VALUES (?,?,?,?,'test','test','test','test','{}','[]','[]','[]',?,?,?)""".trimIndent(),
            id, productId.value, productId.value, status, errorCode, updatedAt, updatedAt,
        )
        return id
    }

    private fun question(request: ProductRequestId, sessionId: String, status: String): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO pf_stakeholder_question(question_id,product_id,agent_role,question,context,process_session_id,
                linked_objects_json,status,created_at,updated_by_type,updated_by_id,version,product_request_id)
                VALUES (?,?,'PRODUCT_DESIGNER_MVP','Welke tekst?','Gerichte aanvraag',?,'[]',?,?,'SYSTEM','retry-test',1,?)""".trimIndent(),
            id, productId.value, sessionId, status, Instant.now(), request.value,
        )
        return id
    }

    private fun workStatus(workId: String): String? = jdbc.queryForObject(
        "SELECT status FROM pf_design_work_item WHERE work_item_id=?", String::class.java, workId,
    )

    companion object {
        private val SYSTEM = ActorReference(ActorType.SYSTEM, "directed-retry-test")
    }
}
