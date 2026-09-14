package nl.vdzon.productfactory.advisor

import com.fasterxml.jackson.databind.JsonNode
import nl.vdzon.productfactory.api.advisor.*
import nl.vdzon.productfactory.api.ai.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Clock
import java.util.UUID
import javax.imageio.ImageIO

@Service
class AdvisorImages(private val jdbc: JdbcTemplate, private val ai: AiExecutionService, private val queries: AiExecutionQueryService, private val clock: Clock) {
    companion object {
        val outputs = (1..4).map { AiOutputArtifactDeclaration("chat-image-0$it", false, setOf("image/png"), 4_194_304) }
    }

    fun publish(conversationId: ProductConversationId, messageId: String, taskId: AiTaskId, result: JsonNode, artifacts: List<ArtifactReference>, context: JsonNode) {
        val images = result.path("images")
        if (images.isMissingNode) { require(artifacts.isEmpty()) { "Afbeeldingen missen een beschrijving." }; return }
        require(images.isArray && images.size() <= 4) { "Ongeldige antwoordafbeeldingen." }
        require(images.map { it.path("artifactName").asText() }.distinct().size == images.size()) { "Dubbele afbeelding." }
        require(artifacts.map { it.name }.toSet() == images.map { it.path("artifactName").asText() }.toSet()) { "Alleen daadwerkelijk opgeleverde beelden kunnen worden getoond." }
        images.forEach { image ->
            val name = image.path("artifactName").asText()
            val artifact = artifacts.single { it.name == name }
            require(outputs.any { it.name == name } && artifact.mediaType == "image/png") { "Onbekend uitvoerbeeld." }
            val match = Regex("/api/ai/tasks/([A-Za-z0-9-]{1,80})/artifacts/([A-Za-z0-9-]{1,80})").matchEntire(artifact.uri) ?: throw InvalidCommand("Het beeld is niet opgeslagen.")
            require(match.groupValues[1] == taskId.value) { "Het beeld hoort niet bij dit antwoord." }
            val kind = image.path("kind").asText()
            require(kind in setOf("SCREENSHOT", "DESIGN", "ILLUSTRATION")) { "Onbekende beeldsoort." }
            val caption = image.path("caption").asText().trim()
            require(caption.length in 1..500) { "Een beeld heeft een korte beschrijving nodig." }
            val environment = image.path("environment").asText()
            val source = image.path("sourceUrl").takeIf { it.isTextual }?.asText()?.let { URI(it) }
            if (source != null) require(source.scheme == "https" && source.host != null && source.userInfo == null) { "Ongeldige beeldbron." }
            if (kind == "SCREENSHOT") {
                require(environment in setOf("PRODUCTION", "ACCEPTANCE") && source != null) { "Een screenshot heeft een pagina en omgeving nodig." }
                val configured = context.path("testConfiguration").path(if (environment == "PRODUCTION") "production" else "acceptance").path("baseUrl").asText()
                val allowed = runCatching { URI(configured) }.getOrNull()
                require(allowed?.host != null && source.scheme == allowed.scheme && source.host == allowed.host && source.port == allowed.port) { "De screenshotbron hoort niet bij de opgegeven productomgeving." }
            } else require(environment == "DESIGN") { "Een ontwerp of illustratie is geen screenshot van de applicatie." }
            queries.openAiTaskArtifact(taskId, match.groupValues[2]).let { content ->
                content.inputStream.use { stream ->
                    require(content.mediaType == "image/png" && content.sizeBytes in 1..4_194_304) { "Het beeld is te groot of ongeldig." }
                    ImageIO.createImageInputStream(stream).use { input ->
                        val readers = ImageIO.getImageReaders(input)
                        require(readers.hasNext()) { "Het bestand is geen leesbare afbeelding." }
                        val reader = readers.next()
                        try {
                            reader.input = input
                            require(reader.formatName.equals("png", true) && reader.getWidth(0).toLong() * reader.getHeight(0) in 1..24_000_000) { "Ongeldig PNG-formaat." }
                            require(reader.read(0) != null) { "Onvolledig PNG-bestand." }
                        } finally { reader.dispose() }
                    }
                }
            }
            // Store only the public origin/path: query strings may contain session details.
            val publicSource = source?.let { URI(it.scheme, null, it.host, it.port, it.path, null, null).toASCIIString() }
            jdbc.update("INSERT INTO pf_advisor_image(id,conversation_id,message_id,task_id,artifact_id,filename,kind,caption,source_url,environment,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID().toString(), conversationId.value, messageId, taskId.value, match.groupValues[2], "$name.png", kind, caption, publicSource, environment, clock.instant())
        }
        ai.retainAiArtifacts(RetainAiArtifactsCommand(artifacts, "CONVERSATION_MESSAGE", messageId, 1))
    }

    fun forMessages(ids: List<String>): List<AdvisorImage> = if (ids.isEmpty()) emptyList() else rows("WHERE message_id IN (${ids.joinToString(",") { "?" }}) ORDER BY created_at,filename", *ids.toTypedArray())
    fun get(id: String): AdvisorImage = rows("WHERE id=?", id).singleOrNull() ?: throw AggregateNotFound("Afbeelding niet gevonden.")
    fun content(id: String): AiArtifactContent {
        val row = jdbc.queryForMap("SELECT task_id,artifact_id FROM pf_advisor_image WHERE id=?", id)
        return queries.openAiTaskArtifact(AiTaskId(row["task_id"].toString()), row["artifact_id"].toString())
    }
    fun recentInputs(conversationId: ProductConversationId, remainingCount: Int, remainingBytes: Long): List<AiInputAttachment> {
        var budget = remainingBytes
        return rows("WHERE conversation_id=? ORDER BY created_at DESC,id DESC LIMIT 4", conversationId.value).take(remainingCount.coerceAtLeast(0)).mapNotNull { image ->
            content(image.id).let { content -> content.inputStream.use { stream ->
                if (content.sizeBytes > budget || content.sizeBytes > 2_097_152) null else {
                    val bytes = stream.readNBytes(4_194_305)
                    require(bytes.size.toLong() == content.sizeBytes) { "Het opgeslagen beeld is onvolledig." }
                    budget -= bytes.size
                    AiInputAttachment("reply-${image.id}", "reply-${image.id}.png", "image/png", AiInputRole.IMAGE, bytes)
                }
            } }
        }
    }
    private fun rows(where: String, vararg params: Any) = jdbc.query("SELECT id,conversation_id,message_id,filename,kind,caption,source_url,environment,created_at FROM pf_advisor_image $where", { rs, _ -> AdvisorImage(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getTimestamp(9).toInstant()) }, *params)
}
