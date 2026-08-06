package nl.vdzon.productfactory.workspace

import kotlin.test.Test
import kotlin.test.assertFailsWith

class WorkspaceRepositoryGuardTest {
    private val guard = WorkspaceRepositoryGuard("git@github.com:robbertvdzon/product-factory-workspace.git")
    @Test fun `accepts only exact workspace repository`() {
        guard.requireWorkspaceRepository("git@github.com:robbertvdzon/product-factory-workspace.git")
        assertFailsWith<IllegalArgumentException> { guard.requireWorkspaceRepository("git@github.com:robbertvdzon/hkh-autopilot.git") }
        assertFailsWith<IllegalArgumentException> { guard.requireWorkspaceRepository("git@github.com:robbertvdzon/product-factory.git") }
    }
}
