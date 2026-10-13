package nl.vdzon.productfactory.design

import nl.vdzon.productfactory.api.foundation.PublicGitRevisionResolver
import nl.vdzon.productfactory.api.shared.CapabilityNotAvailable
import nl.vdzon.productfactory.api.shared.InvalidCommand
import nl.vdzon.productfactory.design.mvp.JGitPublicGitRevisionResolver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand.FastForwardMode
import org.eclipse.jgit.lib.ObjectId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class PublicGitRevisionResolverContractTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `dezelfde en nieuwere gedeployde revisie bevatten alle vereiste commits`() {
        repository { git ->
            val first = commit(git, "first")
            val second = commit(git, "second")
            val deployed = commit(git, "deployed")
            val resolver = LocalRepositoryResolver(git.repository.directory.toPath())

            assertThat(resolver.containsCommits(PUBLIC_URL, second, setOf(second))).isTrue()
            assertThat(resolver.containsCommits(PUBLIC_URL, deployed, setOf(first, second))).isTrue()
            assertCleaned(resolver)
        }
    }

    @Test
    fun `oudere en afwijkende branches zijn aantoonbaar nog niet opgeleverd`() {
        repository { git ->
            val first = commit(git, "first")
            val required = commit(git, "required")
            git.checkout().setCreateBranch(true).setName("other").setStartPoint(first).call()
            val other = commit(git, "other")
            val resolver = LocalRepositoryResolver(git.repository.directory.toPath())

            assertThat(resolver.containsCommits(PUBLIC_URL, first, setOf(required))).isFalse()
            assertThat(resolver.containsCommits(PUBLIC_URL, other, setOf(first, required))).isFalse()
            assertThat(resolver.containsCommits(PUBLIC_URL, required, setOf(other))).isFalse()
            assertCleaned(resolver)
        }
    }

    @Test
    fun `een merge bevat de vereiste commits van beide ouders`() {
        repository { git ->
            val first = commit(git, "first")
            val main = commit(git, "main")
            git.checkout().setCreateBranch(true).setName("other").setStartPoint(first).call()
            val other = commit(git, "other")
            val merge = git.merge().include(ObjectId.fromString(main)).setFastForward(FastForwardMode.NO_FF).call()
            val resolver = LocalRepositoryResolver(git.repository.directory.toPath())

            assertThat(resolver.containsCommits(PUBLIC_URL, merge.newHead.name, setOf(main, other))).isTrue()
            assertCleaned(resolver)
        }
    }

    @Test
    fun `bewezen resultaten worden hergebruikt voor dezelfde onveranderlijke revisies`() {
        repository { git ->
            val first = commit(git, "first")
            val later = commit(git, "later")
            val resolver = LocalRepositoryResolver(git.repository.directory.toPath())

            repeat(2) {
                assertThat(resolver.containsCommits(PUBLIC_URL, later, setOf(first, later))).isTrue()
                assertThat(resolver.containsCommits(PUBLIC_URL, first, setOf(later))).isFalse()
            }
            assertThat(resolver.fetchCalls).isEqualTo(2)
            assertCleaned(resolver)
        }
    }

    @Test
    fun `onbekende gedeployde SHA is een lookupfout en wordt niet gecached`() {
        repository { git ->
            commit(git, "first")
            val resolver = LocalRepositoryResolver(git.repository.directory.toPath())
            val unknown = "f".repeat(40)

            repeat(2) {
                assertThatThrownBy { resolver.containsCommits(PUBLIC_URL, unknown, setOf(unknown)) }
                    .isInstanceOf(InvalidCommand::class.java)
            }
            assertThat(resolver.fetchCalls).isEqualTo(2)
            assertCleaned(resolver)
        }
    }

    @Test
    fun `fetchfout blijft een fout en een latere controle kan slagen`() {
        repository { git ->
            val deployed = commit(git, "deployed")
            val resolver = LocalRepositoryResolver(git.repository.directory.toPath())
            resolver.beforeFetch = { throw IOException("test transport unavailable") }

            assertThatThrownBy { resolver.containsCommits(PUBLIC_URL, deployed, setOf(deployed)) }
                .isInstanceOf(InvalidCommand::class.java)
            assertCleaned(resolver)

            resolver.beforeFetch = {}
            assertThat(resolver.containsCommits(PUBLIC_URL, deployed, setOf(deployed))).isTrue()
            assertThat(resolver.fetchCalls).isEqualTo(2)
            assertCleaned(resolver)
        }
    }

    @Test
    fun `onvolledige geschiedenis bewijst niet dat een commit ontbreekt`() {
        repository { git ->
            val first = commit(git, "first")
            val deployed = commit(git, "deployed")
            val resolver = LocalRepositoryResolver(git.repository.directory.toPath())
            resolver.afterFetch = { fetched ->
                Files.writeString(fetched.repository.directory.toPath().resolve("shallow"), "$deployed\n")
            }

            assertThatThrownBy { resolver.containsCommits(PUBLIC_URL, deployed, setOf(first)) }
                .isInstanceOf(InvalidCommand::class.java)
                .hasMessageContaining("onvolledig")
            assertCleaned(resolver)
        }
    }

    @Test
    fun `alleen begrensde credentialvrije HTTPS URLs en volledige SHAs bereiken fetch`() {
        val resolver = LocalRepositoryResolver(directory)
        val revision = "a".repeat(40)
        listOf(
            directory.toString(), directory.toUri().toString(), "http://example.org/repo.git",
            "https://user:secret@example.org/repo.git", "https://example.org/repo.git?token=secret",
            "https://example.org/repo.git#fragment", "https:///repo.git", "https://example.org",
            "https://example.org/${"a".repeat(1000)}",
        ).forEach { url ->
            assertThatThrownBy { resolver.containsCommits(url, revision, setOf(revision)) }
                .isInstanceOf(InvalidCommand::class.java)
        }
        listOf("HEAD", "abc1234", "$revision:refs/heads/main").forEach { invalid ->
            assertThatThrownBy { resolver.containsCommits(PUBLIC_URL, invalid, setOf(revision)) }
                .isInstanceOf(InvalidCommand::class.java)
            assertThatThrownBy { resolver.containsCommits(PUBLIC_URL, revision, setOf(invalid)) }
                .isInstanceOf(InvalidCommand::class.java)
        }
        assertThat(resolver.containsCommits(PUBLIC_URL, revision, emptySet())).isTrue()
        assertThat(resolver.fetchCalls).isZero()
    }

    @Test
    fun `eenvoudige resolvers claimen geen onbekende commit-afstamming`() {
        val revision = "a".repeat(40)
        val resolver = object : PublicGitRevisionResolver {
            override fun resolveHead(publicGitUrl: String): String = revision
        }
        assertThat(resolver.containsCommits(PUBLIC_URL, revision, emptySet())).isTrue()
        assertThat(resolver.containsCommits(PUBLIC_URL, revision, setOf(revision))).isTrue()
        assertThatThrownBy { resolver.containsCommits(PUBLIC_URL, revision, setOf("b".repeat(40))) }
            .isInstanceOf(CapabilityNotAvailable::class.java)
    }

    private fun repository(test: (Git) -> Unit) {
        Git.init().setDirectory(directory.resolve("repository").toFile()).call().use { git ->
            git.repository.config.apply {
                setString("user", null, "name", "Contract Test")
                setString("user", null, "email", "contract@example.invalid")
                setBoolean("uploadpack", null, "allowReachableSHA1InWant", true)
                save()
            }
            test(git)
        }
    }

    private fun commit(git: Git, message: String): String =
        git.commit().setAllowEmpty(true).setMessage(message)
            .setAuthor("Contract Test", "contract@example.invalid")
            .setCommitter("Contract Test", "contract@example.invalid").call().name

    private fun assertCleaned(resolver: LocalRepositoryResolver) {
        assertThat(resolver.temporaryDirectories).isNotEmpty()
        resolver.temporaryDirectories.forEach { assertThat(it).doesNotExist() }
    }

    private class LocalRepositoryResolver(private val source: Path) : JGitPublicGitRevisionResolver() {
        var fetchCalls = 0
        val temporaryDirectories = mutableListOf<Path>()
        var beforeFetch: (Git) -> Unit = {}
        var afterFetch: (Git) -> Unit = {}

        override fun fetchRevision(git: Git, publicGitUrl: String, deployedRevision: String) {
            fetchCalls++
            temporaryDirectories.add(git.repository.directory.toPath())
            beforeFetch(git)
            // Only the test transport is local; the public entry point still validates HTTPS.
            super.fetchRevision(git, source.toUri().toString(), deployedRevision)
            afterFetch(git)
        }
    }

    companion object {
        private const val PUBLIC_URL = "https://example.org/repository.git"
    }
}
