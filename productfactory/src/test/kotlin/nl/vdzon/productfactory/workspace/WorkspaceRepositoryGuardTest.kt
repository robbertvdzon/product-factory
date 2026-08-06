package nl.vdzon.productfactory.workspace

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkspaceRepositoryGuardTest {
    private val guard = WorkspaceRepositoryGuard("git@github.com:robbertvdzon/product-factory-workspace.git")
    @Test fun `accepts only exact workspace repository`() {
        guard.requireWorkspaceRepository("git@github.com:robbertvdzon/product-factory-workspace.git")
        assertFailsWith<IllegalArgumentException> { guard.requireWorkspaceRepository("git@github.com:robbertvdzon/hkh-autopilot.git") }
        assertFailsWith<IllegalArgumentException> { guard.requireWorkspaceRepository("git@github.com:robbertvdzon/product-factory.git") }
    }

    @Test fun `git authentication uses basic with x access token username`() {
        val encoded = Base64.getEncoder().encodeToString("x-access-token:test-token".toByteArray())
        assertEquals("Authorization: Basic $encoded", gitAuthorizationHeader("test-token"))
    }
}
