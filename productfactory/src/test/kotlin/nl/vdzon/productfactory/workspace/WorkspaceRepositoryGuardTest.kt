package nl.vdzon.productfactory.workspace

import java.nio.file.Path
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkspaceRepositoryGuardTest {
    @Test
    fun `git commands trust only the configured workspace checkout`() {
        val workspace = Path.of("/workspace").toAbsolutePath().normalize()
        assertEquals(
            listOf("git", "-c", "safe.directory=$workspace", "checkout", "main"),
            workspaceGitCommand(workspace, "checkout", "main"),
        )
    }
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
