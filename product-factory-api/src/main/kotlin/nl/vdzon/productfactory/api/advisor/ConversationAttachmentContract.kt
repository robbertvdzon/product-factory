package nl.vdzon.productfactory.api.advisor

import nl.vdzon.productfactory.api.ai.AiInputAttachment
import nl.vdzon.productfactory.api.shared.ProductId

enum class ConversationPurpose { LEGACY, QUESTION, EPIC }
enum class ConversationIntent { AUTO, DISCUSS, UPDATE_EPIC }
data class EpicChatProposal(val id: String, val beforeContentVersion: Long, val afterContentVersion: Long?, val status: String, val summary: String?)
data class ConversationImageInput(val filename: String, val mediaType: String, val base64: String)
data class ConversationAttachment(val id: String, val conversationId: String, val messageId: String, val filename: String, val mediaType: String, val sizeBytes: Long)
interface ConversationAttachmentService {
    fun save(conversationId: ProductConversationId, messageId: ProductConversationMessageId, images: List<ConversationImageInput>)
    fun list(conversationId: ProductConversationId): List<ConversationAttachment>
    fun forDesign(productId: ProductId, epicId: String? = null, conversationId: String? = null): List<ConversationAttachment>
    fun inputs(ids: List<String>): List<AiInputAttachment>
    fun get(id: String): ConversationAttachment
}
