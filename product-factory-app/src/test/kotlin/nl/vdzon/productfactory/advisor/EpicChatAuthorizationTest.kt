package nl.vdzon.productfactory.advisor

import nl.vdzon.productfactory.api.advisor.*
import nl.vdzon.productfactory.api.shared.ProductId
import nl.vdzon.productfactory.auth.ProductAuthorizationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import java.time.Instant

class EpicChatAuthorizationTest {
    private val service = mock(ProductAdvisorApplicationService::class.java)
    private val authorization = mock(ProductAuthorizationService::class.java)
    private val images = mock(ConversationAttachmentService::class.java)
    private val replies = mock(AdvisorImages::class.java)
    private val controller = ProductAdvisorController(service, authorization, mock(JdbcTemplate::class.java), images, replies)
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val product = ProductId("product")
    private val user = UserId("reader")
    private val conversation = ProductConversationDetails(ProductConversationId("chat"), product, "Epicgesprek", UserId("creator"), ConversationStatus.OPEN, 1, now, now, epicId = "epic", audienceRole = ProductMembershipRole.ARCHITECT, purpose = ConversationPurpose.EPIC)

    @Test
    fun `factory owner kan persoonlijk PO gesprek met deelnemers lezen`() {
        val personalConversation = conversation.copy(epicId = null, purpose = ConversationPurpose.QUESTION,
            audienceRole = ProductMembershipRole.PRODUCT_OWNER, participantUserIds = listOf(conversation.createdBy))
        `when`(service.findConversations(product, false)).thenReturn(listOf(personalConversation))
        `when`(service.getConversation(conversation.id, false)).thenReturn(personalConversation)
        `when`(authorization.isFactoryOwner(null)).thenReturn(true)

        assertThat(controller.conversations(product.value, null, false)).containsExactly(personalConversation)
        assertThat(controller.conversation(conversation.id.value, null, false)).isEqualTo(personalConversation)
        verify(authorization, times(2)).requireProduct(product, null)
    }

    @Test
    fun `andere PO ziet persoonlijk gesprek en deelnemers niet`() {
        val personalConversation = conversation.copy(epicId = null, purpose = ConversationPurpose.QUESTION,
            audienceRole = ProductMembershipRole.PRODUCT_OWNER, participantUserIds = listOf(conversation.createdBy))
        `when`(service.findConversations(product, false)).thenReturn(listOf(personalConversation))
        `when`(service.getConversation(conversation.id, false)).thenReturn(personalConversation)
        `when`(authorization.currentUserId(null)).thenReturn(user)
        `when`(authorization.current(null)).thenReturn(UserDetails(user, "po@example.test", null, true, emptySet(), emptyList(), ActingRole.PRODUCT_OWNER))

        assertThat(controller.conversations(product.value, null, false)).isEmpty()
        assertThrows<AccessDeniedException> { controller.conversation(conversation.id.value, null, false) }
    }

    @Test
    fun `PO kan verder in gedeeld gesprek dat door architect gestart is`() {
        `when`(service.getConversation(conversation.id)).thenReturn(conversation)
        `when`(authorization.current(null)).thenReturn(UserDetails(user, "po@example.test", null, true, emptySet(), emptyList(), ActingRole.PRODUCT_OWNER))
        `when`(authorization.currentUserId(null)).thenReturn(user)
        controller.close("chat", ConversationActionRequest(1, "close"), null)
        verify(authorization).requireRole(product, ProductMembershipRole.PRODUCT_OWNER, null)
        verify(service).closeConversation(CloseConversationCommand(conversation.id, 1, user, "close"))
    }

    @Test
    fun `beeld uit persoonlijk vraaggesprek is niet zichtbaar voor ander productlid`() {
        `when`(images.get("image")).thenReturn(ConversationAttachment("image", "chat", "message", "screen.png", "image/png", 100))
        `when`(service.getConversation(conversation.id)).thenReturn(conversation.copy(epicId = null, purpose = ConversationPurpose.QUESTION))
        `when`(authorization.currentUserId(null)).thenReturn(user)
        assertThrows<AccessDeniedException> { controller.image("image", null) }
        verify(images, never()).inputs(listOf("image"))
    }
    @Test
    fun `AI afbeelding uit persoonlijk gesprek wordt geweigerd voor ander productlid`() {
        `when`(replies.get("image")).thenReturn(AdvisorImage("image", "chat", "message", "screen.png", "SCREENSHOT", "Homepage", "https://example.test/", "PRODUCTION", now))
        `when`(service.getConversation(conversation.id, false)).thenReturn(conversation.copy(epicId = null, purpose = ConversationPurpose.QUESTION))
        `when`(authorization.currentUserId(null)).thenReturn(user)
        assertThrows<AccessDeniedException> { controller.replyImage("image", null, true) }
        verify(replies, never()).content("image")
    }

    @Test
    fun `berichtpaginas van persoonlijke gesprekken vereisen dezelfde toegang`() {
        `when`(service.getConversation(conversation.id, false)).thenReturn(conversation.copy(epicId = null, purpose = ConversationPurpose.QUESTION))
        `when`(authorization.currentUserId(null)).thenReturn(user)
        assertThrows<AccessDeniedException> { controller.messages("chat", null, null, null, 30) }
        verify(service, never()).messagePage(listOf(conversation.id), null, null, 30)
    }

}
