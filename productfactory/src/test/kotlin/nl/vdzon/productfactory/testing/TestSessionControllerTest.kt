package nl.vdzon.productfactory.testing

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.WorkspacePublicationView
import nl.vdzon.productfactory.product.CreateProductRequest
import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.workspace.api.WorkspacePublicationPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSessionControllerTest.Fakes::class)
class TestSessionControllerTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper,
    @Autowired private val products: ProductCatalog,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val slug = "test-session-controller-test"

    @BeforeEach
    fun prepare() {
        runCatching { products.create(CreateProductRequest(slug, "Testsessietest", "Test de testsessieflow", status = "active").configuration()) }
        jdbc.update("delete from product_bug where product_slug = ?", slug)
        jdbc.update("delete from test_session where product_slug = ?", slug)
        jdbc.update("delete from agent_run where product_slug = ?", slug)
    }

    @Test
    fun `a test session records tested areas and creates prioritized bugs`() {
        val response = mvc.post("/api/products/$slug/test-sessions").andExpect {
            status { isAccepted() }
            jsonPath("$.status") { value("QUEUED") }
        }.andReturn()
        val id = mapper.readTree(response.response.contentAsString).path("id").asText()
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (System.currentTimeMillis() < deadline) {
            val status = jdbc.queryForObject("select status from test_session where id = ?", String::class.java, id)
            if (status in setOf("COMPLETED", "FAILED")) break
            Thread.sleep(50)
        }

        mvc.get("/api/products/$slug/test-sessions/$id").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.testedAreas") { value(2) }
            jsonPath("$.bugsCreated") { value(1) }
        }
        mvc.get("/api/products/$slug/bugs").andExpect {
            status { isOk() }
            jsonPath("$[0].priority") { value("P0") }
            jsonPath("$[0].sourceType") { value("TEST_SESSION") }
        }
    }

    @TestConfiguration
    class Fakes {
        @Bean @Primary
        fun agents() = AgentDispatchPort { task -> AgentResult(task.runId, "COMPLETED", """{
            "summary":"De kernflow is getest; aanmelden blokkeert volledig.",
            "testedAreas":[
              {"area":"Startpagina","result":"PASS","evidence":"De pagina laadde en navigatie was zichtbaar."},
              {"area":"Aanmelden","result":"FAIL","evidence":"De aanmeldknop reageerde niet na activering."}
            ],
            "bugUpdates":[{"action":"CREATE","bugId":null,"title":"Aanmelden reageert niet","description":"De primaire aanmeldactie doet niets.","reproductionSteps":"Open de startpagina en activeer Aanmelden","expectedResult":"Het aanmeldformulier wordt geopend","actualResult":"Er gebeurt niets en de pagina blijft gelijk","priority":"P0"}]
        }""".trimIndent()) }

        @Bean @Primary
        fun workspace() = WorkspacePublicationPort { artifact -> WorkspacePublicationView(
            artifact.runId, artifact.productSlug, artifact.relativePath, "hash", "COMMITTED_LOCAL", null, "sha",
        ) }
    }
}
