package nl.vdzon.productfactory.product

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.api.quality.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.*
import java.util.HexFormat
import java.util.UUID

@Service
@Transactional
class ProductApplicationService(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val quality: ObjectProvider<QualityService>,
) : ProductCommandService, ProductQueryService {

    override fun createProduct(command: CreateProductCommand): ProductId {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        replay(command.idempotencyKey, "CREATE_PRODUCT", fingerprint)?.let { return ProductId(it) }
        val name = requiredText(command.name, "Productnaam")
        val productId = command.requestedId ?: ProductId(slug(name))
        validateProductId(productId)
        if (exists("SELECT COUNT(*) FROM pf_product WHERE product_id=?", productId.value)) {
            throw InvalidCommand("Product-ID ${productId.value} bestaat al.")
        }
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_product(product_id,name,status,dispatching_enabled,created_at,updated_at,updated_by_type,updated_by_id,version)
               VALUES (?,?,?,?,?,?,?,?,?)""",
            productId.value, name, command.status.name, false, now, now, command.actor.type.name, command.actor.id, 1L,
        )
        ScheduledProcess.entries.forEach { process ->
            jdbc.update(
                """INSERT INTO pf_process_schedule(product_id,process,enabled,timezone,pattern_json,next_run_at,last_scheduled_at,last_skipped_at,updated_at,actor_type,actor_id,version)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
                productId.value, process.name, false, DEFAULT_TIMEZONE, json(SchedulePattern()), null, null, null,
                now, command.actor.type.name, command.actor.id, 1L,
            )
        }
        remember(command.idempotencyKey, "CREATE_PRODUCT", productId.value, fingerprint, productId.value, command.actor, now)
        return productId
    }

    override fun updateProductAssignment(command: UpdateProductAssignmentCommand) {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        if (replay(command.idempotencyKey, "UPDATE_PRODUCT_ASSIGNMENT", fingerprint) != null) return
        requireProduct(command.productId)
        val current = assignmentVersion(command.productId)
        requireVersion(current, command.expectedVersion, "Productopdracht")
        val nextVersion = current + 1
        val audience = requiredText(command.audience, "Doelgroep")
        val goal = requiredText(command.goal, "Productdoel")
        val boundaries = normalizedTextList(command.hardBoundaries, "Harde grenzen")
        val gitUrl = validatePublicGitUrl(command.publicGitUrl)
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_product_assignment(product_id,version,audience,goal,hard_boundaries_json,public_git_url,created_at,actor_type,actor_id)
               VALUES (?,?,?,?,?,?,?,?,?)""",
            command.productId.value, nextVersion, audience, goal, json(boundaries), gitUrl, now,
            command.actor.type.name, command.actor.id,
        )
        remember(command.idempotencyKey, "UPDATE_PRODUCT_ASSIGNMENT", command.productId.value, fingerprint, null, command.actor, now)
    }

    override fun configureTestableProduct(command: ConfigureTestableProductCommand) {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        if (replay(command.idempotencyKey, "CONFIGURE_TESTABLE_PRODUCT", fingerprint) != null) return
        requireProduct(command.productId)
        val current = testConfigurationVersion(command.productId)
        requireVersion(current, command.expectedVersion, "Testconfiguratie")
        val acceptance = validateEnvironment(command.acceptance, false)
        val production = command.production?.let { validateEnvironment(it, true) }
        val nextVersion = current + 1
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_testable_product_configuration(product_id,version,acceptance_json,production_json,created_at,actor_type,actor_id)
               VALUES (?,?,?,?,?,?,?)""",
            command.productId.value, nextVersion, json(acceptance), production?.let(::json), now,
            command.actor.type.name, command.actor.id,
        )
        remember(command.idempotencyKey, "CONFIGURE_TESTABLE_PRODUCT", command.productId.value, fingerprint, null, command.actor, now)
    }

    override fun setProductStatus(command: SetProductStatusCommand) {
        mutateProduct(
            command.productId, command.expectedVersion, command.idempotencyKey, "SET_PRODUCT_STATUS", command.actor,
            fingerprint(command), "status" to command.status.name,
        )
    }

    override fun setProductDispatching(command: SetProductDispatchingCommand) {
        mutateProduct(
            command.productId, command.expectedVersion, command.idempotencyKey, "SET_PRODUCT_DISPATCHING", command.actor,
            fingerprint(command), "dispatching_enabled" to command.enabled,
        )
    }

    override fun setEpicApprovalMode(command: SetEpicApprovalModeCommand) {
        mutateProduct(
            command.productId, command.expectedVersion, command.idempotencyKey, "SET_EPIC_APPROVAL_MODE", command.actor,
            fingerprint(command), "epic_approval_mode" to command.mode.name,
        )
    }

    private fun mutateProduct(
        productId: ProductId,
        expectedVersion: Long,
        idempotencyKey: String,
        commandType: String,
        actor: ActorReference,
        fingerprint: String,
        change: Pair<String, Any>,
    ) {
        validateActor(actor)
        if (replay(idempotencyKey, commandType, fingerprint) != null) return
        val product = getProduct(productId)
        requireVersion(product.version, expectedVersion, "Product")
        val allowedColumns = setOf("status", "dispatching_enabled", "epic_approval_mode")
        check(change.first in allowedColumns)
        val now = clock.instant()
        val updated = jdbc.update(
            "UPDATE pf_product SET ${change.first}=?,updated_at=?,updated_by_type=?,updated_by_id=?,version=version+1 WHERE product_id=? AND version=?",
            change.second, now, actor.type.name, actor.id, productId.value, expectedVersion,
        )
        if (updated != 1) throw VersionConflict("Product is intussen gewijzigd.")
        remember(idempotencyKey, commandType, productId.value, fingerprint, null, actor, now)
    }

    override fun updateProcessSchedule(command: UpdateProcessScheduleCommand) {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        if (replay(command.idempotencyKey, "UPDATE_PROCESS_SCHEDULE", fingerprint) != null) return
        requireProduct(command.productId)
        val current = getProcessSchedule(command.productId, command.process)
        requireVersion(current.version, command.expectedVersion, "Procesplanning")
        val zone = runCatching { ZoneId.of(command.timezone.trim()) }
            .getOrElse { throw InvalidCommand("Ongeldige IANA-tijdzone.") }
        val pattern = normalizePattern(command.pattern, command.enabled)
        val now = clock.instant()
        val nextRunAt = if (command.enabled) nextRun(pattern, zone, now) else null
        val updated = jdbc.update(
            """UPDATE pf_process_schedule SET enabled=?,timezone=?,pattern_json=?,next_run_at=?,updated_at=?,actor_type=?,actor_id=?,version=version+1
               WHERE product_id=? AND process=? AND version=?""",
            command.enabled, zone.id, json(pattern), nextRunAt, now, command.actor.type.name, command.actor.id,
            command.productId.value, command.process.name, command.expectedVersion,
        )
        if (updated != 1) throw VersionConflict("Procesplanning is intussen gewijzigd.")
        remember(command.idempotencyKey, "UPDATE_PROCESS_SCHEDULE", "${command.productId.value}:${command.process}", fingerprint, null, command.actor, now)
    }

    override fun submitUserSignal(command: SubmitUserSignalCommand): UserSignalId {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        replay(command.idempotencyKey, "SUBMIT_USER_SIGNAL", fingerprint)?.let { return UserSignalId(it) }
        requireProduct(command.productId)
        val id = UserSignalId(UUID.randomUUID().toString())
        val source = requiredText(command.source, "Signaalbron")
        val text = requiredText(command.text, "Signaaltekst")
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_user_signal(signal_id,product_id,category,urgency,source,signal_text,attachments_json,status,created_at,updated_at,updated_by_type,updated_by_id,version)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            id.value, command.productId.value, command.category.name, command.urgency.name, source, text,
            json(command.attachments), UserSignalStatus.OPEN.name, now, now, command.actor.type.name, command.actor.id, 1L,
        )
        remember(command.idempotencyKey, "SUBMIT_USER_SIGNAL", id.value, fingerprint, id.value, command.actor, now)
        if (command.category == UserSignalCategory.QUALITY_CONCERN) {
            quality.ifAvailable?.requestSignalInvestigation(RequestSignalInvestigationCommand(
                command.productId, id, 1L, "acceptance", "signal-quality-${id.value}-v1",
            ))
        }
        return id
    }

    override fun markUserSignalInReview(command: MarkUserSignalInReviewCommand) {
        mutateSignal(command.signalId, command.expectedVersion, command.actor, command.idempotencyKey, "MARK_SIGNAL_IN_REVIEW", fingerprint(command)) { signal, now ->
            if (signal.status != UserSignalStatus.OPEN) throw InvalidCommand("Alleen een open signaal kan in behandeling worden genomen.")
            jdbc.update(
                "UPDATE pf_user_signal SET status=?,updated_at=?,updated_by_type=?,updated_by_id=?,version=version+1 WHERE signal_id=? AND version=?",
                UserSignalStatus.IN_REVIEW.name, now, command.actor.type.name, command.actor.id, command.signalId.value, command.expectedVersion,
            )
        }
    }

    override fun recordSignalInvestigation(command: RecordSignalInvestigationCommand) {
        mutateSignal(command.signalId, command.expectedVersion, command.actor, command.idempotencyKey, "RECORD_SIGNAL_INVESTIGATION", fingerprint(command)) { signal, now ->
            if (signal.status == UserSignalStatus.PROCESSED) throw InvalidCommand("Dit signaal is al verwerkt.")
            jdbc.update(
                """UPDATE pf_user_signal SET status=?,verification_id=?,outcome=?,updated_at=?,updated_by_type=?,updated_by_id=?,version=version+1
                   WHERE signal_id=? AND version=?""",
                UserSignalStatus.PROCESSED.name, command.verificationId.value, requiredText(command.outcome, "Onderzoeksuitkomst"),
                now, command.actor.type.name, command.actor.id, command.signalId.value, command.expectedVersion,
            )
        }
    }

    override fun linkSignalToEpic(command: LinkSignalToEpicCommand) {
        if (command.epicVersion <= 0) throw InvalidCommand("Epicversie moet positief zijn.")
        mutateSignal(command.signalId, command.expectedVersion, command.actor, command.idempotencyKey, "LINK_SIGNAL_TO_EPIC", fingerprint(command)) { _, now ->
            jdbc.update(
                """UPDATE pf_user_signal SET epic_id=?,epic_version=?,updated_at=?,updated_by_type=?,updated_by_id=?,version=version+1
                   WHERE signal_id=? AND version=?""",
                command.epicId.value, command.epicVersion, now, command.actor.type.name, command.actor.id,
                command.signalId.value, command.expectedVersion,
            )
        }
    }

    private fun mutateSignal(
        signalId: UserSignalId,
        expectedVersion: Long,
        actor: ActorReference,
        idempotencyKey: String,
        commandType: String,
        fingerprint: String,
        mutation: (UserSignalDetails, Instant) -> Unit,
    ) {
        validateActor(actor)
        if (replay(idempotencyKey, commandType, fingerprint) != null) return
        val signal = getUserSignal(signalId)
        requireVersion(signal.version, expectedVersion, "Gebruikerssignaal")
        val now = clock.instant()
        mutation(signal, now)
        remember(idempotencyKey, commandType, signalId.value, fingerprint, null, actor, now)
    }

    override fun askStakeholder(command: AskStakeholderCommand): StakeholderQuestionId {
        validateActor(command.actor)
        if (command.actor.type !in setOf(ActorType.PROCESS, ActorType.SYSTEM)) {
            throw InvalidCommand("Alleen vertrouwde procescode kan een Stakeholdervraag stellen.")
        }
        val fingerprint = fingerprint(command)
        replay(command.idempotencyKey, "ASK_STAKEHOLDER", fingerprint)?.let { return StakeholderQuestionId(it) }
        requireProduct(command.productId)
        val id = StakeholderQuestionId(UUID.randomUUID().toString())
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_stakeholder_question(question_id,product_id,agent_role,question,context,process_session_id,linked_objects_json,status,created_at,updated_by_type,updated_by_id,version)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
            id.value, command.productId.value, requiredText(command.agentRole, "Agentrol"), requiredText(command.question, "Vraag"),
            requiredText(command.context, "Vraagcontext"), command.processSessionId.value, json(command.linkedObjects),
            StakeholderQuestionStatus.OPEN.name, now, command.actor.type.name, command.actor.id, 1L,
        )
        appendQuestionToOpenMeetings(command.productId, id, command.question, now)
        remember(command.idempotencyKey, "ASK_STAKEHOLDER", id.value, fingerprint, id.value, command.actor, now)
        return id
    }

    override fun recordStakeholderAnswer(command: RecordStakeholderAnswerCommand) {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        if (replay(command.idempotencyKey, "RECORD_STAKEHOLDER_ANSWER", fingerprint) != null) return
        val question = getStakeholderQuestion(command.questionId)
        requireVersion(question.version, command.expectedVersion, "Stakeholdervraag")
        if (question.status != StakeholderQuestionStatus.OPEN) throw InvalidCommand("Alleen een open vraag kan worden beantwoord.")
        val meeting = getMeeting(command.meetingId)
        if (meeting.productId != question.productId) throw InvalidCommand("Vraag en overleg horen niet bij hetzelfde product.")
        val sourceMessage = meeting.messages.singleOrNull { it.id == command.messageId }
            ?: throw InvalidCommand("Het opgegeven Stakeholderbericht bestaat niet in dit overleg.")
        if (sourceMessage.senderRole != MeetingSenderRole.STAKEHOLDER || sourceMessage.text.trim() != command.answer.trim()) {
            throw InvalidCommand("Het antwoord moet exact uit het opgegeven Stakeholderbericht komen.")
        }
        val now = clock.instant()
        val updated = jdbc.update(
            """UPDATE pf_stakeholder_question SET status=?,answer=?,meeting_id=?,answer_message_id=?,answered_at=?,updated_by_type=?,updated_by_id=?,version=version+1
               WHERE question_id=? AND version=?""",
            StakeholderQuestionStatus.ANSWERED.name, command.answer.trim(), command.meetingId.value, command.messageId,
            now, command.actor.type.name, command.actor.id, command.questionId.value, command.expectedVersion,
        )
        if (updated != 1) throw VersionConflict("Stakeholdervraag is intussen gewijzigd.")
        remember(command.idempotencyKey, "RECORD_STAKEHOLDER_ANSWER", command.questionId.value, fingerprint, null, command.actor, now)
    }

    override fun withdrawStakeholderQuestion(command: WithdrawStakeholderQuestionCommand) {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        if (replay(command.idempotencyKey, "WITHDRAW_STAKEHOLDER_QUESTION", fingerprint) != null) return
        val question = getStakeholderQuestion(command.questionId)
        requireVersion(question.version, command.expectedVersion, "Stakeholdervraag")
        if (question.status != StakeholderQuestionStatus.OPEN) throw InvalidCommand("Alleen een open vraag kan worden ingetrokken.")
        val now = clock.instant()
        val updated = jdbc.update(
            """UPDATE pf_stakeholder_question SET status=?,withdrawal_reason=?,withdrawn_at=?,updated_by_type=?,updated_by_id=?,version=version+1
               WHERE question_id=? AND version=?""",
            StakeholderQuestionStatus.WITHDRAWN.name, requiredText(command.reason, "Intrekkingsreden"), now,
            command.actor.type.name, command.actor.id, command.questionId.value, command.expectedVersion,
        )
        if (updated != 1) throw VersionConflict("Stakeholdervraag is intussen gewijzigd.")
        remember(command.idempotencyKey, "WITHDRAW_STAKEHOLDER_QUESTION", command.questionId.value, fingerprint, null, command.actor, now)
    }

    override fun startMeeting(command: StartMeetingCommand): MeetingId {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        replay(command.idempotencyKey, "START_MEETING", fingerprint)?.let { return MeetingId(it) }
        requireProduct(command.productId)
        val id = MeetingId(UUID.randomUUID().toString())
        val openQuestions = findStakeholderQuestions(
            StakeholderQuestionFilter(productId = command.productId, statuses = setOf(StakeholderQuestionStatus.OPEN)),
        )
        val agenda = (normalizedTextList(command.agenda, "Agenda", allowEmpty = true) +
            openQuestions.map { "Vraag van ${it.agentRole}: ${it.question}" }).distinct()
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_meeting(meeting_id,product_id,reason,agenda_json,linked_objects_json,status,minutes,outcomes_json,created_at,updated_by_type,updated_by_id,version)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
            id.value, command.productId.value, requiredText(command.reason, "Aanleiding"), json(agenda), json(command.linkedObjects),
            if (command.requested) MeetingStatus.REQUESTED.name else MeetingStatus.OPEN.name, null, json(emptyList<String>()),
            now, command.actor.type.name, command.actor.id, 1L,
        )
        remember(command.idempotencyKey, "START_MEETING", id.value, fingerprint, id.value, command.actor, now)
        return id
    }

    override fun recordMeetingMessage(command: RecordMeetingMessageCommand) {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        if (replay(command.idempotencyKey, "RECORD_MEETING_MESSAGE", fingerprint) != null) return
        val meeting = getMeeting(command.meetingId)
        requireVersion(meeting.version, command.expectedVersion, "Overleg")
        if (meeting.status == MeetingStatus.CLOSED) throw InvalidCommand("Een gesloten overleg accepteert geen berichten.")
        if (command.senderRole == MeetingSenderRole.STAKEHOLDER && command.actor.type != ActorType.STAKEHOLDER) {
            throw InvalidCommand("Alleen de Stakeholder kan een Stakeholderbericht registreren.")
        }
        if (command.senderRole == MeetingSenderRole.MEETING_AGENT && command.actor.type !in setOf(ActorType.PROCESS, ActorType.SYSTEM)) {
            throw InvalidCommand("Alleen vertrouwde overlegcode kan een Meeting Agent-bericht registreren.")
        }
        val now = clock.instant()
        val sequence = meeting.messages.size.toLong() + 1
        val messageId = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO pf_meeting_message(message_id,meeting_id,sender_role,represented_agent_role,message_text,created_at,actor_type,actor_id,sequence_number)
               VALUES (?,?,?,?,?,?,?,?,?)""",
            messageId, command.meetingId.value, command.senderRole.name, command.representedAgentRole,
            requiredText(command.text, "Bericht"), now, command.actor.type.name, command.actor.id, sequence,
        )
        val updated = jdbc.update(
            "UPDATE pf_meeting SET status=?,updated_by_type=?,updated_by_id=?,version=version+1 WHERE meeting_id=? AND version=?",
            MeetingStatus.OPEN.name, command.actor.type.name, command.actor.id, command.meetingId.value, command.expectedVersion,
        )
        if (updated != 1) throw VersionConflict("Overleg is intussen gewijzigd.")
        remember(command.idempotencyKey, "RECORD_MEETING_MESSAGE", command.meetingId.value, fingerprint, messageId, command.actor, now)
    }

    override fun closeMeeting(command: CloseMeetingCommand) {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        if (replay(command.idempotencyKey, "CLOSE_MEETING", fingerprint) != null) return
        val meeting = getMeeting(command.meetingId)
        requireVersion(meeting.version, command.expectedVersion, "Overleg")
        if (meeting.status == MeetingStatus.CLOSED) throw InvalidCommand("Het overleg is al gesloten.")
        val now = clock.instant()
        val updated = jdbc.update(
            """UPDATE pf_meeting SET status=?,minutes=?,outcomes_json=?,closed_at=?,updated_by_type=?,updated_by_id=?,version=version+1
               WHERE meeting_id=? AND version=?""",
            MeetingStatus.CLOSED.name, requiredText(command.minutes, "Notulen"), json(normalizeOutcomes(command.outcomes)),
            now, command.actor.type.name, command.actor.id, command.meetingId.value, command.expectedVersion,
        )
        if (updated != 1) throw VersionConflict("Overleg is intussen gewijzigd.")
        remember(command.idempotencyKey, "CLOSE_MEETING", command.meetingId.value, fingerprint, null, command.actor, now)
    }

    @Transactional(readOnly = true)
    override fun getProduct(productId: ProductId): ProductDetails = try {
        jdbc.queryForObject(
            "SELECT * FROM pf_product WHERE product_id=?",
            { rs, _ -> product(rs) }, productId.value,
        )!!
    } catch (_: EmptyResultDataAccessException) {
        throw AggregateNotFound("Product ${productId.value} bestaat niet.")
    }

    @Transactional(readOnly = true)
    override fun findProducts(): List<ProductDetails> = jdbc.query(
        "SELECT * FROM pf_product ORDER BY name,product_id",
    ) { rs, _ -> product(rs) }

    @Transactional(readOnly = true)
    override fun getProductAssignment(productId: ProductId): ProductAssignmentDetails {
        requireProduct(productId)
        return try {
            jdbc.queryForObject(
                "SELECT * FROM pf_product_assignment WHERE product_id=? ORDER BY version DESC LIMIT 1",
                { rs, _ -> ProductAssignmentDetails(
                    productId, rs.getString("audience"), rs.getString("goal"), readStringList(rs.getString("hard_boundaries_json")),
                    rs.getString("public_git_url"), rs.getLong("version"),
                ) }, productId.value,
            )!!
        } catch (_: EmptyResultDataAccessException) {
            throw AggregateNotFound("Productopdracht voor ${productId.value} is nog niet vastgelegd.")
        }
    }

    @Transactional(readOnly = true)
    override fun getTestableProduct(productId: ProductId): TestableProductDetails {
        requireProduct(productId)
        return try {
            jdbc.queryForObject(
                "SELECT * FROM pf_testable_product_configuration WHERE product_id=? ORDER BY version DESC LIMIT 1",
                { rs, _ -> TestableProductDetails(
                    productId,
                    mapper.readValue(rs.getString("acceptance_json"), TestEnvironmentConfiguration::class.java),
                    rs.getString("production_json")?.let { mapper.readValue(it, TestEnvironmentConfiguration::class.java) },
                    rs.getLong("version"),
                ) }, productId.value,
            )!!
        } catch (_: EmptyResultDataAccessException) {
            throw AggregateNotFound("Testconfiguratie voor ${productId.value} is nog niet vastgelegd.")
        }
    }

    @Transactional(readOnly = true)
    override fun getProcessSchedule(productId: ProductId, process: ScheduledProcess): ProcessScheduleDetails = try {
        jdbc.queryForObject(
            "SELECT * FROM pf_process_schedule WHERE product_id=? AND process=?",
            { rs, _ -> schedule(rs) }, productId.value, process.name,
        )!!
    } catch (_: EmptyResultDataAccessException) {
        throw AggregateNotFound("Procesplanning voor ${productId.value} en $process bestaat niet.")
    }

    @Transactional(readOnly = true)
    override fun getProcessSchedules(productId: ProductId): List<ProcessScheduleDetails> {
        requireProduct(productId)
        return jdbc.query(
            "SELECT * FROM pf_process_schedule WHERE product_id=? ORDER BY process",
            { rs, _ -> schedule(rs) }, productId.value,
        )
    }

    @Transactional(readOnly = true)
    override fun findScheduleRuns(productId: ProductId?): List<ScheduleRunDetails> = jdbc.query(
        """SELECT id,product_id,process,scheduled_for,status,result_summary,error_code,claimed_at,finished_at
            FROM pf_schedule_run ${if (productId == null) "" else "WHERE product_id=?"} ORDER BY claimed_at DESC""".trimIndent(),
        { rs, _ -> ScheduleRunDetails(
            rs.getString(1), ProductId(rs.getString(2)), ScheduledProcess.valueOf(rs.getString(3)), rs.getTimestamp(4).toInstant(),
            ScheduleRunStatus.valueOf(rs.getString(5)), rs.getString(6), rs.getString(7), rs.getTimestamp(8).toInstant(), rs.getTimestamp(9)?.toInstant(),
        ) }, *listOfNotNull(productId?.value).toTypedArray(),
    )

    @Transactional(readOnly = true)
    override fun getUserSignal(userSignalId: UserSignalId): UserSignalDetails = try {
        jdbc.queryForObject(
            "SELECT * FROM pf_user_signal WHERE signal_id=?",
            { rs, _ -> signal(rs) }, userSignalId.value,
        )!!
    } catch (_: EmptyResultDataAccessException) {
        throw AggregateNotFound("Gebruikerssignaal ${userSignalId.value} bestaat niet.")
    }

    @Transactional(readOnly = true)
    override fun findUserSignals(filter: UserSignalFilter): List<UserSignalDetails> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        filter.productId?.let { clauses += "product_id=?"; args += it.value }
        enumFilter("status", filter.statuses.map { it.name }, clauses, args)
        enumFilter("category", filter.categories.map { it.name }, clauses, args)
        enumFilter("urgency", filter.urgencies.map { it.name }, clauses, args)
        filter.source?.takeIf { it.isNotBlank() }?.let { clauses += "LOWER(source)=LOWER(?)"; args += it.trim() }
        filter.timeRange.from?.let { clauses += "created_at>=?"; args += it }
        filter.timeRange.until?.let { clauses += "created_at<?"; args += it }
        val where = if (clauses.isEmpty()) "" else " WHERE ${clauses.joinToString(" AND ")}"
        return jdbc.query("SELECT * FROM pf_user_signal$where ORDER BY created_at DESC,signal_id", { rs, _ -> signal(rs) }, *args.toTypedArray())
    }

    @Transactional(readOnly = true)
    override fun getStakeholderQuestion(questionId: StakeholderQuestionId): StakeholderQuestionDetails = try {
        jdbc.queryForObject(
            "SELECT * FROM pf_stakeholder_question WHERE question_id=?",
            { rs, _ -> question(rs) }, questionId.value,
        )!!
    } catch (_: EmptyResultDataAccessException) {
        throw AggregateNotFound("Stakeholdervraag ${questionId.value} bestaat niet.")
    }

    @Transactional(readOnly = true)
    override fun findStakeholderQuestions(filter: StakeholderQuestionFilter): List<StakeholderQuestionDetails> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        filter.productId?.let { clauses += "product_id=?"; args += it.value }
        filter.agentRole?.takeIf { it.isNotBlank() }?.let { clauses += "agent_role=?"; args += it.trim() }
        enumFilter("status", filter.statuses.map { it.name }, clauses, args)
        val where = if (clauses.isEmpty()) "" else " WHERE ${clauses.joinToString(" AND ")}"
        return jdbc.query(
            "SELECT * FROM pf_stakeholder_question$where ORDER BY CASE status WHEN 'OPEN' THEN 0 ELSE 1 END,created_at DESC,question_id",
            { rs, _ -> question(rs) }, *args.toTypedArray(),
        )
    }

    @Transactional(readOnly = true)
    override fun getMeeting(meetingId: MeetingId): MeetingDetails = try {
        jdbc.queryForObject(
            "SELECT * FROM pf_meeting WHERE meeting_id=?",
            { rs, _ -> meeting(rs) }, meetingId.value,
        )!!
    } catch (_: EmptyResultDataAccessException) {
        throw AggregateNotFound("Overleg ${meetingId.value} bestaat niet.")
    }

    @Transactional(readOnly = true)
    override fun findMeetings(productId: ProductId, status: MeetingStatus?): List<MeetingDetails> {
        requireProduct(productId)
        return if (status == null) {
            jdbc.query("SELECT * FROM pf_meeting WHERE product_id=? ORDER BY created_at DESC", { rs, _ -> meeting(rs) }, productId.value)
        } else {
            jdbc.query("SELECT * FROM pf_meeting WHERE product_id=? AND status=? ORDER BY created_at DESC", { rs, _ -> meeting(rs) }, productId.value, status.name)
        }
    }

    fun deleteAllOwnedData() {
        jdbc.update("DELETE FROM pf_meeting_message")
        jdbc.update("DELETE FROM pf_stakeholder_question")
        jdbc.update("DELETE FROM pf_meeting")
        jdbc.update("DELETE FROM pf_user_signal")
        jdbc.update("DELETE FROM pf_schedule_run")
        jdbc.update("DELETE FROM pf_process_schedule")
        jdbc.update("DELETE FROM pf_testable_product_configuration")
        jdbc.update("DELETE FROM pf_product_assignment")
        jdbc.update("DELETE FROM pf_product_command")
        jdbc.update("DELETE FROM pf_product")
    }

    private fun product(rs: ResultSet) = ProductDetails(
        ProductId(rs.getString("product_id")), rs.getString("name"), ProductStatus.valueOf(rs.getString("status")),
        rs.getBoolean("dispatching_enabled"), instant(rs, "created_at")!!, rs.getLong("version"),
        EpicApprovalMode.valueOf(rs.getString("epic_approval_mode")),
    )

    private fun schedule(rs: ResultSet) = ProcessScheduleDetails(
        ProductId(rs.getString("product_id")), ScheduledProcess.valueOf(rs.getString("process")), rs.getBoolean("enabled"),
        rs.getString("timezone"), mapper.readValue(rs.getString("pattern_json"), SchedulePattern::class.java),
        instant(rs, "next_run_at"), instant(rs, "updated_at")!!, rs.getLong("version"),
    )

    private fun signal(rs: ResultSet) = UserSignalDetails(
        UserSignalId(rs.getString("signal_id")), ProductId(rs.getString("product_id")), UserSignalCategory.valueOf(rs.getString("category")),
        UserSignalUrgency.valueOf(rs.getString("urgency")), rs.getString("source"), rs.getString("signal_text"),
        mapper.readValue(rs.getString("attachments_json"), object : TypeReference<List<ArtifactReference>>() {}),
        UserSignalStatus.valueOf(rs.getString("status")), rs.getString("verification_id")?.let(::VerificationId),
        rs.getString("outcome"), rs.getString("epic_id")?.let(::EpicId), rs.getObject("epic_version")?.let { rs.getLong("epic_version") },
        instant(rs, "created_at")!!, rs.getLong("version"),
    )

    private fun question(rs: ResultSet) = StakeholderQuestionDetails(
        StakeholderQuestionId(rs.getString("question_id")), ProductId(rs.getString("product_id")), rs.getString("agent_role"),
        rs.getString("question"), rs.getString("context"), ProcessSessionId(rs.getString("process_session_id")),
        mapper.readValue(rs.getString("linked_objects_json"), object : TypeReference<List<SourceReference>>() {}),
        StakeholderQuestionStatus.valueOf(rs.getString("status")), rs.getString("answer"), rs.getString("meeting_id")?.let(::MeetingId),
        rs.getString("answer_message_id"), rs.getString("withdrawal_reason"), instant(rs, "created_at")!!,
        instant(rs, "answered_at"), instant(rs, "withdrawn_at"), rs.getLong("version"),
    )

    private fun meeting(rs: ResultSet): MeetingDetails {
        val meetingId = rs.getString("meeting_id")
        val messages = jdbc.query(
            "SELECT * FROM pf_meeting_message WHERE meeting_id=? ORDER BY sequence_number",
            { message, _ -> MeetingMessageDetails(
                message.getString("message_id"), MeetingSenderRole.valueOf(message.getString("sender_role")),
                message.getString("represented_agent_role"), message.getString("message_text"), instant(message, "created_at")!!,
            ) }, meetingId,
        )
        return MeetingDetails(
            MeetingId(meetingId), ProductId(rs.getString("product_id")), rs.getString("reason"), readStringList(rs.getString("agenda_json")),
            mapper.readValue(rs.getString("linked_objects_json"), object : TypeReference<List<SourceReference>>() {}),
            MeetingStatus.valueOf(rs.getString("status")), messages, rs.getString("minutes"),
            mapper.readValue(rs.getString("outcomes_json"), object : TypeReference<List<MeetingOutcomeDetails>>() {}),
            instant(rs, "created_at")!!, instant(rs, "closed_at"), rs.getLong("version"),
        )
    }

    private fun appendQuestionToOpenMeetings(productId: ProductId, questionId: StakeholderQuestionId, text: String, now: Instant) {
        jdbc.query("SELECT * FROM pf_meeting WHERE product_id=? AND status IN ('REQUESTED','OPEN')", { rs, _ ->
            val agenda = readStringList(rs.getString("agenda_json")).toMutableList()
            val item = "Vraag ${questionId.value}: $text"
            if (item !in agenda) {
                agenda += item
                jdbc.update("UPDATE pf_meeting SET agenda_json=?,version=version+1 WHERE meeting_id=?", json(agenda), rs.getString("meeting_id"))
            }
        }, productId.value)
    }

    private fun assignmentVersion(productId: ProductId): Long = jdbc.queryForObject(
        "SELECT COALESCE(MAX(version),0) FROM pf_product_assignment WHERE product_id=?", Long::class.java, productId.value,
    ) ?: 0L

    private fun testConfigurationVersion(productId: ProductId): Long = jdbc.queryForObject(
        "SELECT COALESCE(MAX(version),0) FROM pf_testable_product_configuration WHERE product_id=?", Long::class.java, productId.value,
    ) ?: 0L

    private fun normalizePattern(pattern: SchedulePattern, enabled: Boolean): SchedulePattern {
        val hasWeekly = pattern.weeklyRules.isNotEmpty()
        val hasInterval = pattern.intervalMinutes != null
        if (hasWeekly && hasInterval) throw InvalidCommand("Weekregels en interval mogen niet worden gemengd.")
        if (enabled && !hasWeekly && !hasInterval) throw InvalidCommand("Een ingeschakeld schema vereist weekregels of een interval.")
        pattern.intervalMinutes?.let {
            if (it !in 1..525_600) throw InvalidCommand("Interval moet tussen 1 minuut en 1 jaar liggen.")
            return SchedulePattern(intervalMinutes = it)
        }
        val seen = linkedSetOf<Pair<DayOfWeek, LocalTime>>()
        pattern.weeklyRules.forEach { rule ->
            if (rule.days.isEmpty() || rule.times.isEmpty()) throw InvalidCommand("Iedere weekregel vereist dagen en tijden.")
            rule.days.sorted().forEach { day -> rule.times.sorted().forEach { time -> seen += day to time.withSecond(0).withNano(0) } }
        }
        return SchedulePattern(weeklyRules = seen.map { (day, time) -> WeeklyScheduleRule(setOf(day), setOf(time)) })
    }

    private fun nextRun(pattern: SchedulePattern, zone: ZoneId, now: Instant): Instant {
        pattern.intervalMinutes?.let { return now.plusSeconds(it * 60) }
        val localNow = now.atZone(zone)
        return (0..8).flatMap { offset ->
            val date = localNow.toLocalDate().plusDays(offset.toLong())
            pattern.weeklyRules.filter { date.dayOfWeek in it.days }.flatMap { rule ->
                rule.times.flatMap { time -> validInstants(LocalDateTime.of(date, time), zone) }
            }
        }.filter { it.isAfter(now) }.minOrNull()
            ?: throw InvalidCommand("Het weekschema levert geen volgend tijdstip op.")
    }

    private fun validInstants(local: LocalDateTime, zone: ZoneId): List<Instant> {
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.isNotEmpty()) return offsets.map { local.toInstant(it) }
        val transition = zone.rules.getTransition(local) ?: return emptyList()
        return listOf(transition.dateTimeAfter.atZone(zone).toInstant())
    }

    private fun validateEnvironment(input: TestEnvironmentConfiguration, production: Boolean): TestEnvironmentConfiguration {
        val name = requiredText(input.name, "Omgevingsnaam")
        val baseUrl = validateHttpsUrl(input.baseUrl, "Omgevings-URL")
        val routes = input.allowedRoutes.map { route ->
            val normalized = route.trim()
            if (!normalized.startsWith('/') || normalized.contains("..")) throw InvalidCommand("Toegestane routes moeten veilige absolute paden zijn.")
            normalized
        }.distinct()
        if (routes.isEmpty()) throw InvalidCommand("Minimaal één toegestane route is verplicht.")
        val revisionEndpoint = input.revisionEndpoint.trim()
        if (!revisionEndpoint.startsWith('/') || revisionEndpoint.contains("..")) throw InvalidCommand("Ongeldig revisionendpoint.")
        val revisionPath = input.revisionJsonPath.trim().removePrefix("$.")
        if (!revisionPath.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,100}"))) throw InvalidCommand("Ongeldige revisionregel.")
        val data = normalizedTextList(input.dataBoundaries, "Datagrenzen", true)
        val access = normalizedTextList(input.accessBoundaries, "Toegangsgrenzen", true)
        if (production && access.isEmpty()) throw InvalidCommand("Productie vereist expliciete toegangsgrenzen.")
        return TestEnvironmentConfiguration(name, baseUrl, routes, revisionEndpoint, revisionPath, data, access)
    }

    private fun validatePublicGitUrl(value: String): String {
        val url = validateHttpsUrl(value, "Publieke Git-URL")
        val uri = URI(url)
        if (!uri.path.endsWith(".git") || uri.query != null || uri.fragment != null || uri.userInfo != null) {
            throw InvalidCommand("Publieke Git-URL moet een HTTPS .git-URL zonder credentials, query of fragment zijn.")
        }
        return url
    }

    private fun validateHttpsUrl(value: String, label: String): String {
        val uri = runCatching { URI(value.trim()) }.getOrNull()
        if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) {
            throw InvalidCommand("$label moet een publieke HTTPS-URL zonder credentials of fragment zijn.")
        }
        return uri.toString().removeSuffix("/")
    }

    private fun validateProductId(productId: ProductId) {
        if (!PRODUCT_ID.matches(productId.value)) {
            throw InvalidCommand("Product-ID moet 3–100 tekens bevatten, alleen kleine letters, cijfers en koppeltekens gebruiken en mag niet met een koppelteken beginnen of eindigen.")
        }
    }

    private fun validateActor(actor: ActorReference) {
        if (actor.id.isBlank() || actor.id.length > 320) throw InvalidCommand("Actor is ongeldig.")
    }

    private fun requireProduct(productId: ProductId) { getProduct(productId) }
    private fun requireVersion(actual: Long, expected: Long, label: String) {
        if (actual != expected) throw VersionConflict("$label is intussen gewijzigd: verwacht $expected, actueel $actual.")
    }

    private fun requiredText(value: String, label: String): String = value.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }
        ?: throw InvalidCommand("$label is verplicht en mag maximaal $MAX_TEXT tekens bevatten.")

    private fun normalizedTextList(values: List<String>, label: String, allowEmpty: Boolean = false): List<String> {
        val result = values.map(String::trim).filter(String::isNotEmpty).distinct()
        if (!allowEmpty && result.isEmpty()) throw InvalidCommand("$label mag niet leeg zijn.")
        if (result.any { it.length > MAX_TEXT }) throw InvalidCommand("$label bevat te lange tekst.")
        return result
    }

    private fun normalizeOutcomes(outcomes: List<MeetingOutcomeDetails>): List<MeetingOutcomeDetails> = outcomes.map { outcome ->
        val description = requiredText(outcome.description, "Doorwerking")
        val commandType = requiredText(outcome.commandType, "Commandtype")
        if (commandType.length > 100) throw InvalidCommand("Commandtype mag maximaal 100 tekens bevatten.")
        val error = outcome.errorCode?.trim()?.takeIf(String::isNotEmpty)
        if (outcome.status == MeetingOutcomeStatus.FAILED && error == null) {
            throw InvalidCommand("Een mislukte doorwerking vereist een foutcode.")
        }
        if (outcome.status == MeetingOutcomeStatus.SUCCEEDED && error != null) {
            throw InvalidCommand("Een geslaagde doorwerking heeft geen foutcode.")
        }
        outcome.copy(description = description, commandType = commandType, errorCode = error)
    }

    private fun enumFilter(column: String, values: List<String>, clauses: MutableList<String>, args: MutableList<Any>) {
        if (values.isEmpty()) return
        clauses += "$column IN (${values.joinToString(",") { "?" }})"
        args.addAll(values)
    }

    private fun slug(value: String): String {
        val base = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(90)
        if (base.length < 3) throw InvalidCommand("Productnaam levert geen bruikbaar stabiel ID op.")
        return base
    }

    private fun replay(idempotencyKey: String, commandType: String, fingerprint: String): String? {
        validateIdempotencyKey(idempotencyKey)
        val rows = jdbc.query(
            "SELECT command_type,request_fingerprint,result_id FROM pf_product_command WHERE idempotency_key=?",
            { rs, _ -> Triple(rs.getString(1), rs.getString(2), rs.getString(3)) }, idempotencyKey,
        )
        val row = rows.singleOrNull() ?: return null
        if (row.first != commandType || row.second != fingerprint) throw IdempotencyConflict("Idempotentiesleutel is al voor andere inhoud gebruikt.")
        return row.third ?: REPLAYED_WITHOUT_RESULT
    }

    private fun remember(
        key: String,
        type: String,
        aggregateId: String,
        fingerprint: String,
        resultId: String?,
        actor: ActorReference,
        now: Instant,
    ) {
        jdbc.update(
            """INSERT INTO pf_product_command(idempotency_key,command_type,aggregate_id,request_fingerprint,result_id,actor_type,actor_id,applied_at)
               VALUES (?,?,?,?,?,?,?,?)""",
            key, type, aggregateId, fingerprint, resultId, actor.type.name, actor.id, now,
        )
    }

    private fun validateIdempotencyKey(key: String) {
        if (!IDEMPOTENCY_KEY.matches(key)) throw InvalidCommand("Idempotentiesleutel is ongeldig.")
    }

    private fun fingerprint(value: Any): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)),
    )

    private fun json(value: Any): String = mapper.writeValueAsString(value)
    private fun readStringList(value: String): List<String> = mapper.readValue(value, object : TypeReference<List<String>>() {})
    private fun exists(sql: String, vararg args: Any): Boolean = (jdbc.queryForObject(sql, Long::class.java, *args) ?: 0L) > 0
    private fun instant(rs: ResultSet, column: String): Instant? = rs.getObject(column, OffsetDateTime::class.java)?.toInstant()

    companion object {
        private const val DEFAULT_TIMEZONE = "Europe/Amsterdam"
        private const val MAX_TEXT = 200_000
        private const val REPLAYED_WITHOUT_RESULT = "__REPLAYED__"
        private val PRODUCT_ID = Regex("[a-z0-9][a-z0-9-]{1,98}[a-z0-9]")
        private val IDEMPOTENCY_KEY = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{2,199}")
        private val ENVIRONMENT_KEY = Regex("[A-Z][A-Z0-9_]*__[A-Z][A-Z0-9_]*")
    }
}
