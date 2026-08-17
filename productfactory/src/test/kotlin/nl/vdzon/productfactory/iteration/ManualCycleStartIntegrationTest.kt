package nl.vdzon.productfactory.iteration

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import nl.vdzon.productfactory.contracts.ManualStartOrigin
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ManualCycleStartIntegrationTest(
    @Autowired private val service: ShadowIterationService,
    @Autowired private val repository: ShadowIterationRepository,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val mvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val meterRegistry: MeterRegistry,
) {
    /**
     * Deze suite bewijst de starttransactie. Laat de asynchrone engine de zojuist aangemaakte QUEUED-rij niet al
     * volledig afhandelen voordat de tweede gelijktijdige bevestiging de product-lock krijgt; dat zou de productie-
     * invariant die hier wordt getest vervangen door een timingtest van de snelle mock-agent.
     */
    @MockitoBean
    private lateinit var iterationRunner: ShadowIterationRunner

    @BeforeEach
    fun createProducts() {
        listOf(
            VALIDATION_PRODUCT_SLUG,
            HTTP_PRODUCT_SLUG,
            HTTP_SUCCESS_PRODUCT_SLUG,
            PRIVACY_PRODUCT_SLUG,
            CONCURRENT_PRODUCT_SLUG,
            AUTOMATIC_PRODUCT_SLUG,
        ).forEach { slug ->
            if ((jdbc.queryForObject("select count(*) from product_definition where slug = ?", Long::class.java, slug) ?: 0) == 0L) {
                jdbc.update(
                    """insert into product_definition(
                        id, slug, name, mission, guardrails, status, workspace_ownership, development_mode
                    ) values (?, ?, ?, ?, ?, 'active', 'product-factory', 'autonomous')""".trimIndent(),
                    "product-$slug",
                    slug,
                    "Manual start test",
                    "Test manual starts",
                    "Test only",
                )
            }
        }
    }

    @Test
    fun `manual origins validate against focus and rejected combinations create nothing`() {
        assertFailsWith<ResponseStatusException> {
            service.startManualCycle(VALIDATION_PRODUCT_SLUG, "Andere opdracht", ManualStartOrigin.AUTONOMOUS_DEFAULT)
        }
        assertFailsWith<ResponseStatusException> {
            service.startManualCycle(VALIDATION_PRODUCT_SLUG, "   ", ManualStartOrigin.OWNER_INPUT)
        }
        assertFailsWith<ResponseStatusException> {
            service.startManualCycle(VALIDATION_PRODUCT_SLUG, "x".repeat(301), ManualStartOrigin.OWNER_INPUT)
        }
        MANUAL_START_EDGE_WHITESPACE.forEach { whitespace ->
            assertFailsWith<ResponseStatusException> {
                service.startManualCycle(VALIDATION_PRODUCT_SLUG, whitespace, ManualStartOrigin.OWNER_INPUT)
            }
            assertEquals("Vraag", trimManualStartFocus("${whitespace}Vraag$whitespace"))
        }
        assertEquals("\u200BVraag\u200B", trimManualStartFocus("\u200BVraag\u200B"))
        assertEquals(0, repository.list(VALIDATION_PRODUCT_SLUG).size)

        val owner = service.startManualCycle(VALIDATION_PRODUCT_SLUG, "  Vraag  met  binnenruimte  ", ManualStartOrigin.OWNER_INPUT)
        assertEquals("Vraag  met  binnenruimte", owner.focus)
        assertEquals(ManualStartOrigin.OWNER_INPUT, owner.manualStartOrigin)
    }

    @Test
    fun `unknown manual origin is rejected by the HTTP contract without creating a cycle`() {
        mvc.post("/api/products/$HTTP_PRODUCT_SLUG/cycles") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"focus":"vrije geheime vraag","manualStartOrigin":"UNKNOWN"}"""
        }.andExpect { status { isBadRequest() } }

        assertEquals(0, repository.list(HTTP_PRODUCT_SLUG).size)
    }

    @Test
    fun `HTTP owner input is stored and returned byte equal with its provenance`() {
        val response = mvc.post("/api/products/$HTTP_SUCCESS_PRODUCT_SLUG/cycles") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"focus":"Vraag  met  binnenruimte","manualStartOrigin":"OWNER_INPUT"}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.productSlug") { value(HTTP_SUCCESS_PRODUCT_SLUG) }
            jsonPath("$.focus") { value("Vraag  met  binnenruimte") }
            jsonPath("$.manualStartOrigin") { value("OWNER_INPUT") }
        }.andReturn()

        val id = objectMapper.readTree(response.response.contentAsString).path("id").asText()
        mvc.get("/api/shadow-iterations/$id") {
            param("productSlug", HTTP_SUCCESS_PRODUCT_SLUG)
        }.andExpect {
            status { isOk() }
            jsonPath("$.focus") { value("Vraag  met  binnenruimte") }
            jsonPath("$.manualStartOrigin") { value("OWNER_INPUT") }
        }
    }

    @Test
    fun `rejected owner input stays out of backend responses logs and telemetry`() {
        val privateFocus = "PRIVACY_SENTINEL_${"x".repeat(301)}"
        val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val capturedLogs = ListAppender<ILoggingEvent>().also {
            it.start()
            rootLogger.addAppender(it)
        }

        val response = try {
            mvc.post("/api/products/$PRIVACY_PRODUCT_SLUG/cycles") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    mapOf("focus" to privateFocus, "manualStartOrigin" to "OWNER_INPUT"),
                )
            }.andExpect { status { isBadRequest() } }.andReturn().response
        } finally {
            rootLogger.detachAppender(capturedLogs)
            capturedLogs.stop()
        }

        assertFalse(response.contentAsString.contains(privateFocus))
        assertFalse(response.errorMessage.orEmpty().contains(privateFocus))
        val logOutput = capturedLogs.list.joinToString("\n") { event ->
            buildString {
                append(event.formattedMessage)
                event.throwableProxy?.let { append(ThrowableProxyUtil.asString(it)) }
            }
        }
        assertFalse(logOutput.contains(privateFocus))

        val requestMeters = meterRegistry.find("http.server.requests").meters()
        assertTrue(requestMeters.isNotEmpty(), "De HTTP-requesttelemetry moet actief zijn voor dit bewijs")
        val telemetryMetadata = meterRegistry.meters.joinToString("\n") { it.id.toString() }
        assertFalse(telemetryMetadata.contains(privateFocus))
        assertEquals(0, repository.list(PRIVACY_PRODUCT_SLUG).size)
    }

    @Test
    fun `concurrent confirmations create at most one cycle and second request leaves no extra row`(
        applicationEvents: ApplicationEvents,
    ) {
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val attempts = (1..2).map {
                pool.submit<Result<String>> {
                    ready.countDown()
                    start.await()
                    runCatching {
                        service.startManualCycle(
                            CONCURRENT_PRODUCT_SLUG,
                            ShadowIterationService.AUTONOMOUS_DEFAULT_FOCUS,
                            ManualStartOrigin.AUTONOMOUS_DEFAULT,
                        ).id
                    }
                }
            }
            ready.await(5, TimeUnit.SECONDS)
            start.countDown()
            val results = attempts.map { it.get(10, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.exceptionOrNull() is ResponseStatusException })
            val iterations = repository.list(CONCURRENT_PRODUCT_SLUG)
            assertEquals(1, iterations.size)
            assertEquals(
                1,
                applicationEvents.stream(ShadowIterationStarted::class.java)
                    .filter { it.iterationId == iterations.single().id }
                    .count(),
            )
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `automatic start retains standard behavior without manual provenance and detail stays product scoped`() {
        val automatic = service.startCycle(AUTOMATIC_PRODUCT_SLUG, null)
        assertEquals(ShadowIterationService.AUTONOMOUS_DEFAULT_FOCUS, automatic.focus)
        assertNull(automatic.manualStartOrigin)

        mvc.get("/api/shadow-iterations/${automatic.id}") {
            param("productSlug", HTTP_PRODUCT_SLUG)
        }.andExpect { status { isNotFound() } }
    }

    private companion object {
        const val VALIDATION_PRODUCT_SLUG = "manual-cycle-validation-test"
        const val HTTP_PRODUCT_SLUG = "manual-cycle-http-test"
        const val HTTP_SUCCESS_PRODUCT_SLUG = "manual-cycle-http-success-test"
        const val PRIVACY_PRODUCT_SLUG = "manual-cycle-privacy-test"
        const val CONCURRENT_PRODUCT_SLUG = "manual-cycle-concurrent-test"
        const val AUTOMATIC_PRODUCT_SLUG = "automatic-cycle-start-test"
        val MANUAL_START_EDGE_WHITESPACE = listOf(
            "\u0009", "\u000A", "\u000B", "\u000C", "\u000D", "\u0020", "\u0085", "\u00A0", "\u1680",
            "\u2000", "\u200A", "\u2028", "\u2029", "\u202F", "\u205F", "\u3000", "\uFEFF",
        )
    }
}
