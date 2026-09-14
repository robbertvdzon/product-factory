package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.api.advisor.*
import nl.vdzon.productfactory.api.ai.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Base64
import java.util.UUID

@Service
class ConversationAttachments(private val jdbc: JdbcTemplate, private val clock: Clock) : ConversationAttachmentService {
    override fun save(conversationId: ProductConversationId, messageId: ProductConversationMessageId, images: List<ConversationImageInput>) {
        require(images.size <= 6) { "Voeg maximaal zes afbeeldingen per bericht toe." }
        val decoded = images.map { image ->
            require(image.filename.length in 1..200 && !image.filename.contains('/') && !image.filename.contains('\\')) { "Ongeldige bestandsnaam." }
            require(image.base64.length <= 5_600_000) { "Een afbeelding mag maximaal 4 MB zijn." }
            val bytes = Base64.getDecoder().decode(image.base64)
            require(bytes.size in 1..4_194_304) { "Een afbeelding mag maximaal 4 MB zijn." }
            val png = bytes.size >= 8 && bytes.take(8) == listOf(137,80,78,71,13,10,26,10).map(Int::toByte)
            val jpeg = bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
            val webp = bytes.size >= 12 && String(bytes,0,4,Charsets.US_ASCII)=="RIFF" && String(bytes,8,4,Charsets.US_ASCII)=="WEBP"
            require(when(image.mediaType) { "image/png" -> png; "image/jpeg" -> jpeg; "image/webp" -> webp; else -> false }) { "Gebruik een PNG-, JPEG- of WebP-afbeelding." }
            image to bytes
        }
        require(decoded.sumOf { it.second.size } <= 8_388_608) { "Voeg maximaal 8 MB afbeeldingen per bericht toe." }
        val current = list(conversationId)
        require(current.size + images.size <= 10 && current.sumOf { it.sizeBytes } + decoded.sumOf { it.second.size } <= 10_485_760) { "Dit gesprek kan maximaal tien referentiebeelden van samen 10 MB bevatten." }
        decoded.forEach { (image, bytes) -> jdbc.update(
            "INSERT INTO pf_conversation_attachment(id,conversation_id,message_id,filename,media_type,size_bytes,content_base64,created_at) VALUES (?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),conversationId.value,messageId.value,image.filename,image.mediaType,bytes.size.toLong(),Base64.getEncoder().encodeToString(bytes),clock.instant()) }
    }
    private fun rows(where: String, vararg args: Any): List<ConversationAttachment> = jdbc.query(
        "SELECT a.id,a.conversation_id,a.message_id,a.filename,a.media_type,a.size_bytes FROM pf_conversation_attachment a $where ORDER BY a.created_at,a.id",
        {rs,_->ConversationAttachment(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getLong(6))},*args)
    override fun list(conversationId: ProductConversationId) = rows("WHERE a.conversation_id=?",conversationId.value)
    override fun get(id: String) = rows("WHERE a.id=?",id).singleOrNull() ?: throw AggregateNotFound("Afbeelding niet gevonden.")
    override fun forDesign(productId: ProductId, epicId: String?, conversationId: String?): List<ConversationAttachment> = when {
        conversationId != null -> rows("JOIN pf_product_conversation c ON c.conversation_id=a.conversation_id WHERE c.product_id=? AND c.conversation_id=?",productId.value,conversationId)
        epicId != null -> rows("JOIN pf_product_conversation c ON c.conversation_id=a.conversation_id WHERE c.product_id=? AND c.epic_id=?",productId.value,epicId)
        else -> emptyList()
    }
    override fun inputs(ids: List<String>): List<AiInputAttachment> = ids.distinct().map { id ->
        val info=get(id)
        val encoded=jdbc.queryForObject("SELECT content_base64 FROM pf_conversation_attachment WHERE id=?",String::class.java,id)!!
        val suffix=when(info.mediaType){"image/png"->"png";"image/jpeg"->"jpg";else->"webp"}
        AiInputAttachment("reference-$id","reference-$id.$suffix",info.mediaType,AiInputRole.IMAGE,Base64.getDecoder().decode(encoded))
    }
}
