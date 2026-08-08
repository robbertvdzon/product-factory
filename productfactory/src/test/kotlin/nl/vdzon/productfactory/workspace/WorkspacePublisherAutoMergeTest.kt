package nl.vdzon.productfactory.workspace

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regressietest voor een eerdere bug: de auto-merge mutation escapete de node-ID handmatig
 * (`\\\"$nodeId\\\"`), wat een ongeldige GraphQL-query opleverde die GitHub stilzwijgend met
 * HTTP 200 + een errors-body afwees. Auto-merge werd daardoor nooit ingeschakeld en PR's
 * bleven voor altijd open staan, waardoor er nooit stories bij de Software Factory aankwamen.
 */
class WorkspacePublisherAutoMergeTest {
    @Test fun `mutation passes the node id as a GraphQL variable instead of an inline string`() {
        val body = enableAutoMergeMutation("PR_kwDOTvmjdM78SQj4")

        val query = body["query"] as String
        assertFalse(query.contains("\\\""), "query mag geen handmatig geescapete quotes bevatten: $query")
        assertTrue(query.contains("\$id"), "query moet de node-ID via een \$id-variabele doorgeven")
        assertFalse(query.contains("PR_kwDOTvmjdM78SQj4"), "de node-ID hoort niet inline in de querytekst te staan")

        @Suppress("UNCHECKED_CAST")
        val variables = body["variables"] as Map<String, Any>
        assertEquals("PR_kwDOTvmjdM78SQj4", variables["id"])
    }

    @Test fun `serialized request body round-trips through a real JSON parser without malformed escapes`() {
        val mapper = ObjectMapper()
        val nodeId = "PR_kwDOTvmjdM78SQj4"
        val json = mapper.writeValueAsString(enableAutoMergeMutation(nodeId))

        val parsed = mapper.readTree(json)
        val query = parsed["query"].asText()
        assertEquals(nodeId, parsed["variables"]["id"].asText())
        assertTrue(query.contains("enablePullRequestAutoMerge"))
        assertTrue(query.contains("mergeMethod: SQUASH"))
    }
}
