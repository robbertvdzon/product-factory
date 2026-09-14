package nl.vdzon.productfactory.advisor

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.ai.AiExecutionApplicationService
import nl.vdzon.productfactory.ai.FakeRuntime
import nl.vdzon.productfactory.ai.RuntimeV2ArtifactView
import nl.vdzon.productfactory.api.advisor.*
import nl.vdzon.productfactory.api.foundation.PublicGitRevisionResolver
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.auth.UserIdentityRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false", "PF_AGENT_RUNTIME_API_VERSION=v2", "PF_SOFTWARE_FACTORY_MODE=MOCKED", "PF_AI_ARTIFACT_STORAGE_PATH=\${java.io.tmpdir}/product-factory-ai-test"])
@ActiveProfiles("test")
@Import(ProductAdvisorIntegrationTest.Fakes::class)
class AdvisorImagesIntegrationTest(
    @Autowired private val advisor: ProductAdvisorApplicationService,
    @Autowired private val images: AdvisorImages,
    @Autowired private val products: ProductCommandService,
    @Autowired private val users: UserIdentityRepository,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val ai: AiExecutionApplicationService,
    @Autowired private val runtime: FakeRuntime,
    @Autowired private val mapper: ObjectMapper,
) {
    @MockitoBean private lateinit var git: PublicGitRevisionResolver

    @Test
    fun `screenshot wordt duurzaam gepubliceerd gepagineerd en hergebruikt zonder dubbele beelden`() {
        advisor.deleteAllOwnedData()
        ai.deleteAllOwnedExecutionData()
        runtime.reset()
        `when`(git.resolveHead(anyString())).thenReturn("a".repeat(40))
        val product = ProductId("images-${UUID.randomUUID().toString().take(8)}")
        val actor = ActorReference(ActorType.SYSTEM, "test")
        val owner = users.resolveOrCreate("${product.value}@example.test", true)
        products.createProduct(CreateProductCommand(product, "Beelden", actor = actor, idempotencyKey = "create-${product.value}"))
        products.updateProductAssignment(UpdateProductAssignmentCommand(product, "", "Test", "https://github.com/example/product.git", 0, actor, "assignment-${product.value}", "openai", "gpt-5.6-sol"))
        products.configureTestableProduct(ConfigureTestableProductCommand(product, TestEnvironmentConfiguration("Acceptatie", "https://example.test", listOf("/"), "/version", "commit"), null, 0, actor, "env-${product.value}"))
        val conversation = advisor.createConversation(CreateConversationCommand(product, "Screenshot", owner.id, "chat-${product.value}", purpose = ConversationPurpose.QUESTION))
        advisor.addMessage(AddConversationMessageCommand(conversation, "Laat de homepage zien", 1, owner.id, "ask-${product.value}"))
        advisor.resumeAdvisorTurns()
        ai.dispatchPending()
        assertThat(runtime.v2Requests.single().output.artifacts.map { it.name }).containsExactly("chat-image-01", "chat-image-02", "chat-image-03", "chat-image-04")
        val job = runtime.onlyJob()
        val url = "/v2/jobs/${job.id}/objects/image-1/content"
        val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(20, 12, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        runtime.artifactBytes[url] = png
        runtime.v2ResultArtifacts[job.id] = listOf(RuntimeV2ArtifactView("image-1", "chat-image-01", "chat-image-01.png", "image/png", png.size.toLong(), java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)), "READY", Instant.now(), Instant.now(), url))
        val result = mapper.readTree("""{"message":"Hier is het homescherm.","outcome":"ANSWER","observations":[],"proposal":null,"images":[{"artifactName":"chat-image-01","kind":"SCREENSHOT","caption":"Het huidige homescherm","sourceUrl":"https://example.test/?session=private#top","environment":"ACCEPTANCE"}]}""")
        runtime.results[job.id] = result
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        ai.reconcileActive()
        advisor.resumeAdvisorTurns()
        advisor.resumeAdvisorTurns()
        val details = advisor.getConversation(conversation)
        assertThat(details.status).withFailMessage("%s", jdbc.queryForList("SELECT safe_error_code FROM pf_product_advisor_turn WHERE conversation_id=?", conversation.value)).isEqualTo(ConversationStatus.WAITING_FOR_USER)
        val image = details.messages.last().images.single()
        assertThat(image.sourceUrl).isEqualTo("https://example.test/")
        assertThat(image.filename).isEqualTo("chat-image-01.png")
        assertThat(images.content(image.id).inputStream.use { it.readBytes() }).isEqualTo(png)
        assertThat(advisor.messagePage(listOf(conversation), null, null, 1).messages.single().images).containsExactly(image)
        assertThat(advisor.getConversation(conversation, false).messages).isEmpty()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_ai_artifact_domain_reference WHERE domain_id=?", Long::class.java, image.messageId)).isEqualTo(1L)
        advisor.addMessage(AddConversationMessageCommand(conversation, "Wat zie je bovenaan dit beeld?", details.version, owner.id, "follow-${product.value}"))
        advisor.resumeAdvisorTurns()
        ai.dispatchPending()
        assertThat(runtime.v2Requests.last().input.objects.map { it.name }).contains("reply-${image.id}")
        assertThat(runtime.uploadedText()).contains(image.id, "SCREENSHOT")

        val taskId = AiTaskId(jdbc.queryForObject("SELECT task_id FROM pf_advisor_image WHERE id=?", String::class.java, image.id)!!)
        val artifacts = ai.getAiTaskResult(taskId)!!.artifacts
        val context = mapper.readTree("""{"testConfiguration":{"acceptance":{"baseUrl":"https://example.test"}}}""")
        val wrong = result.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        (wrong.path("images").first() as com.fasterxml.jackson.databind.node.ObjectNode).put("sourceUrl", "https://unrelated.test/")
        assertThatThrownBy { images.publish(conversation, "unused", taskId, wrong, artifacts, context) }.hasMessageContaining("productomgeving")
        assertThatThrownBy { images.publish(conversation, "unused", AiTaskId("wrong-task"), result, artifacts, context) }.hasMessageContaining("niet bij dit antwoord")
        assertThatThrownBy { images.publish(conversation, "unused", taskId, result, emptyList(), context) }.hasMessageContaining("daadwerkelijk opgeleverde")
    }
}
