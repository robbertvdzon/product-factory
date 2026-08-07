package nl.vdzon.productfactory.workspace

import org.junit.jupiter.api.Test
import nl.vdzon.productfactory.workspace.api.WorkspaceArtifact
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
class WorkspacePublisherIntegrationTest(@Autowired private val publisher: WorkspacePublisher) {
    @Test fun `approved artifact is committed exactly once for a run id`() {
        val request = WorkspaceArtifact(
            "run-phase2-001", "hkh-autopilot", "research/phase-2-proof.md",
            """---
product: hkh-autopilot
artifact_type: research
run_id: run-phase2-001
date: 2026-08-06
status: approved
sources: []
---
# Fase 2 publicatiebewijs
"""
        )
        val first = publisher.publish(request)
        val second = publisher.publish(request)
        assertEquals(first, second)
        assertEquals("COMMITTED_LOCAL", first.status)
        assertTrue(Files.exists(workspace.resolve("products/hkh-autopilot/research/phase-2-proof.md")))
        assertEquals("2", git("rev-list", "--all", "--count").trim())
    }

    @Test fun `owner workspace and paths outside the allowlist are rejected`() {
        val ownerError = assertFailsWith<org.springframework.web.server.ResponseStatusException> {
            publisher.publish(WorkspaceArtifact("run-owner-001", "hkh", "research/no-write.md", "# Niet schrijven"))
        }
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ownerError.statusCode)

        val pathError = assertFailsWith<org.springframework.web.server.ResponseStatusException> {
            publisher.publish(WorkspaceArtifact("run-path-001", "hkh-autopilot", "private/secret.md", "# Niet toegestaan"))
        }
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, pathError.statusCode)

        assertFailsWith<IllegalArgumentException> {
            publisher.publish(WorkspaceArtifact("run-traversal-001", "hkh-autopilot", "research/../../outside.md", "# Buiten workspace"))
        }
        assertTrue(!Files.exists(workspace.resolve("products/hkh/research/no-write.md")))
        assertTrue(!Files.exists(workspace.resolve("products/hkh-autopilot/private/secret.md")))
        assertTrue(!Files.exists(workspace.resolve("outside.md")))
    }

    companion object {
        private val workspace: Path = Files.createTempDirectory("pf-workspace").also { root ->
            command(root, "git", "init", "-b", "main")
            root.resolve("products/hkh-autopilot/research").createDirectories()
            root.resolve("README.md").writeText("# test workspace\n")
            command(root, "git", "add", ".")
            command(root, "git", "-c", "user.name=Test", "-c", "user.email=test@example.test", "commit", "-m", "initial")
            command(root, "git", "remote", "add", "origin", "git@github.com:robbertvdzon/product-factory-workspace.git")
        }

        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("product-factory.workspace.path") { workspace.toString() }
            registry.add("product-factory.workspace.repository") { "git@github.com:robbertvdzon/product-factory-workspace.git" }
            registry.add("product-factory.workspace.main-branch") { "main" }
            registry.add("product-factory.workspace.remote-publication") { "false" }
        }

        private fun git(vararg args: String) = command(workspace, "git", *args)
        private fun command(directory: Path, vararg args: String): String {
            val process = ProcessBuilder(*args).directory(directory.toFile()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { output }
            return output
        }
    }
}
