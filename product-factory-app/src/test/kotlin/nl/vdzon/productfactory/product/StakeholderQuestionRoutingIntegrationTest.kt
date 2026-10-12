package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.api.advisor.ProductMembershipRole
import nl.vdzon.productfactory.api.advisor.ProductRequestId
import nl.vdzon.productfactory.api.advisor.UserId
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.auth.UserIdentityRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false"])
@ActiveProfiles("test")
@Transactional
class StakeholderQuestionRoutingIntegrationTest(
    @Autowired private val products: ProductCommandService,
    @Autowired private val queries: ProductQueryService,
    @Autowired private val users: UserIdentityRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @Test
    fun `architectvraag bij PO-aanvraag gaat naar architect en functionele vraag naar aanvrager`() {
        val product = product()
        member(product, ProductMembershipRole.PRODUCT_OWNER)
        val requester = member(product, ProductMembershipRole.PRODUCT_OWNER)
        val architect = member(product, ProductMembershipRole.ARCHITECT)
        val request = request(product, requester)

        val architectureCommand = question(product, ProductMembershipRole.ARCHITECT).copy(productRequestId = request)
        val architecture = products.askStakeholder(architectureCommand)
        assertThat(products.askStakeholder(architectureCommand)).isEqualTo(architecture)
        val architecturalQuestion = queries.getStakeholderQuestion(architecture)
        assertThat(architecturalQuestion.requestedRole).isEqualTo(ProductMembershipRole.ARCHITECT)
        assertThat(architecturalQuestion.requestedRespondentUserId).isEqualTo(architect)
        assertThat(architecturalQuestion.productRequestId).isEqualTo(request)
        assertThat(notificationRecipients(architecture)).containsExactly(architect.value)

        val functional = products.askStakeholder(question(product, ProductMembershipRole.PRODUCT_OWNER).copy(productRequestId = request))
        assertThat(queries.getStakeholderQuestion(functional).requestedRespondentUserId).isEqualTo(requester)
        assertThat(notificationRecipients(functional)).containsExactly(requester.value)
    }

    @Test
    fun `expliciete geadresseerde moet de gevraagde rol hebben`() {
        val product = product()
        val owner = member(product, ProductMembershipRole.PRODUCT_OWNER)
        member(product, ProductMembershipRole.ARCHITECT)

        assertThatThrownBy {
            products.askStakeholder(question(product, ProductMembershipRole.ARCHITECT).copy(requestedRespondentUserId = owner))
        }.isInstanceOf(InvalidCommand::class.java)
            .hasMessageContaining("gevraagde productrol")
        assertThat(queries.findStakeholderQuestions(StakeholderQuestionFilter(product))).isEmpty()
    }

    @Test
    fun `meerdere architecten krijgen de rolgerichte vraag en ieder een melding`() {
        val product = product()
        val requester = member(product, ProductMembershipRole.PRODUCT_OWNER)
        val firstArchitect = member(product, ProductMembershipRole.ARCHITECT)
        val secondArchitect = member(product, ProductMembershipRole.ARCHITECT)
        val command = question(product, ProductMembershipRole.ARCHITECT).copy(productRequestId = request(product, requester))

        val id = products.askStakeholder(command)
        assertThat(products.askStakeholder(command)).isEqualTo(id)
        val question = queries.getStakeholderQuestion(id)
        assertThat(question.requestedRole).isEqualTo(ProductMembershipRole.ARCHITECT)
        assertThat(question.requestedRespondentUserId).isNull()
        assertThat(notificationRecipients(id)).containsExactlyInAnyOrder(firstArchitect.value, secondArchitect.value)
    }

    @Test
    fun `expliciete factory owner mag vragen voor beide rollen ontvangen`() {
        val product = product()
        val factoryOwner = users.resolveOrCreate("factory-${product.value}@example.test", true).id
        ProductMembershipRole.entries.forEach { role ->
            val id = products.askStakeholder(question(product, role).copy(requestedRespondentUserId = factoryOwner))
            assertThat(queries.getStakeholderQuestion(id).requestedRespondentUserId).isEqualTo(factoryOwner)
        }
    }

    @Test
    fun `automatische routering negeert inactieve accounts en ingetrokken rollen`() {
        val product = product()
        val inactive = member(product, ProductMembershipRole.ARCHITECT)
        jdbc.update("UPDATE pf_user_account SET active=FALSE WHERE user_id=?", inactive.value)
        val revoked = member(product, ProductMembershipRole.ARCHITECT)
        users.revokeProductOwner(revoked, product, "Rol ingetrokken", revoked, 1, "revoke-${product.value}", ProductMembershipRole.ARCHITECT)
        val requester = member(product, ProductMembershipRole.PRODUCT_OWNER)
        val request = request(product, requester)

        val unassigned = products.askStakeholder(question(product, ProductMembershipRole.ARCHITECT).copy(productRequestId = request))
        assertThat(queries.getStakeholderQuestion(unassigned).requestedRespondentUserId).isNull()
        assertThat(notificationRecipients(unassigned)).isEmpty()

        val available = member(product, ProductMembershipRole.ARCHITECT)
        val assigned = products.askStakeholder(question(product, ProductMembershipRole.ARCHITECT).copy(productRequestId = request))
        assertThat(queries.getStakeholderQuestion(assigned).requestedRespondentUserId).isEqualTo(available)
    }

    private fun product(): ProductId = ProductId("question-role-${UUID.randomUUID().toString().take(8)}").also {
        products.createProduct(CreateProductCommand(it, "Vraagroutering", actor = PROCESS, idempotencyKey = "create-${it.value}"))
    }

    private fun member(product: ProductId, role: ProductMembershipRole): UserId {
        val id = users.resolveOrCreate("${UUID.randomUUID()}@example.test", false).id
        users.grantProductOwner(id, product, id, 0, "grant-${id.value}", role)
        return id
    }

    private fun request(product: ProductId, requester: UserId): ProductRequestId {
        val conversation = UUID.randomUUID().toString()
        val request = ProductRequestId(UUID.randomUUID().toString())
        jdbc.update(
            """INSERT INTO pf_product_conversation(conversation_id,product_id,title,created_by,status,created_at,updated_at,version)
                VALUES (?,?,?,?,'OPEN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)""",
            conversation, product.value, "Aanvraag van product owner", requester.value,
        )
        jdbc.update(
            """INSERT INTO pf_product_request(request_id,product_id,conversation_id,requested_by,request_type,status,current_version,created_at,updated_at,version)
                VALUES (?,?,?,?,'EPIC_CANDIDATE','ROUTING',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)""",
            request.value, product.value, conversation, requester.value,
        )
        return request
    }

    private fun question(product: ProductId, role: ProductMembershipRole) = AskStakeholderCommand(
        product, "PRODUCT_DESIGNER_MVP", "Mag deze wijziging binnen de productafspraken?", "Uitwerking van de aanvraag",
        ProcessSessionId(UUID.randomUUID().toString()), actor = PROCESS, idempotencyKey = UUID.randomUUID().toString(), requestedRole = role,
    )

    private fun notificationRecipients(question: StakeholderQuestionId): List<String> = jdbc.query(
        "SELECT recipient_user_id FROM pf_personal_notification WHERE target_type='QUESTION' AND target_id=?",
        { rs, _ -> rs.getString(1) }, question.value,
    )

    companion object {
        private val PROCESS = ActorReference(ActorType.PROCESS, "question-routing-test")
    }
}
