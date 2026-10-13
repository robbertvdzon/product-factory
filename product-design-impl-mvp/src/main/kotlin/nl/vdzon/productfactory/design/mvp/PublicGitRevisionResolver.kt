package nl.vdzon.productfactory.design.mvp

import nl.vdzon.productfactory.api.shared.InvalidCommand
import nl.vdzon.productfactory.api.foundation.PublicGitRevisionResolver
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.SubmoduleConfig.FetchRecurseSubmodulesMode
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.TagOpt
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Files
import java.util.Collections

@Component
open class JGitPublicGitRevisionResolver : PublicGitRevisionResolver {
    private data class AncestryCheck(val url: String, val deployed: String, val required: Set<String>)

    private val ancestryResults = Collections.synchronizedMap(
        object : LinkedHashMap<AncestryCheck, Boolean>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AncestryCheck, Boolean>): Boolean = size > CACHE_SIZE
        },
    )

    override fun resolveHead(publicGitUrl: String): String {
        validatePublicUrl(publicGitUrl)
        return runCatching {
            Git.lsRemoteRepository().setRemote(publicGitUrl).setTimeout(TIMEOUT_SECONDS)
                .setCredentialsProvider(null).call()
                .singleOrNull { it.name == "HEAD" }?.objectId?.name
        }.getOrNull()?.takeIf { SHA.matches(it) }
            ?: throw InvalidCommand("De publieke Git-HEAD kon niet naar een exacte commit-SHA worden opgelost.")
    }

    override fun containsCommits(publicGitUrl: String, deployedRevision: String, requiredCommitShas: Set<String>): Boolean {
        validatePublicUrl(publicGitUrl)
        if (!SHA.matches(deployedRevision) || requiredCommitShas.any { !SHA.matches(it) }) {
            throw InvalidCommand("De gedeployde en vereiste Git-revisies moeten volledige commit-SHA's zijn.")
        }
        if (requiredCommitShas.isEmpty()) return true
        val cacheKey = AncestryCheck(publicGitUrl, deployedRevision, requiredCommitShas.toSet())
        ancestryResults[cacheKey]?.let { return it }

        val directory = Files.createTempDirectory("pf-deployment-ancestry-").toFile()
        val result = try {
            Git.init().setBare(true).setDirectory(directory).call().use { git ->
                git.repository.config.apply {
                    setString("http", null, "followRedirects", "false")
                    setBoolean("http", null, "sslVerify", true)
                }
                fetchRevision(git, publicGitUrl, deployedRevision)
                RevWalk(git.repository).use { walk ->
                    if (walk.objectReader.shallowCommits.isNotEmpty()) {
                        throw InvalidCommand("De Git-geschiedenis is onvolledig; commit-afstamming is niet vastgesteld.")
                    }
                    val deployed = walk.parseCommit(ObjectId.fromString(deployedRevision))
                    requiredCommitShas.all { sha ->
                        val required = try {
                            walk.parseCommit(ObjectId.fromString(sha))
                        } catch (_: MissingObjectException) {
                            // A full fetch of the deployed revision includes every ancestor.
                            return@all false
                        }
                        walk.isMergedInto(required, deployed)
                    }
                }
            }
        } catch (exception: InvalidCommand) {
            throw exception
        } catch (_: Exception) {
            throw InvalidCommand("De Git-geschiedenis van de gedeployde revisie kon niet worden gecontroleerd.")
        } finally {
            if (!directory.deleteRecursively()) {
                throw InvalidCommand("De tijdelijke Git-gegevens konden niet worden opgeruimd.")
            }
        }
        ancestryResults[cacheKey] = result
        return result
    }

    protected open fun fetchRevision(git: Git, publicGitUrl: String, deployedRevision: String) {
        // No depth or filter: absence of a required commit is conclusive only with complete ancestry.
        git.fetch().setRemote(publicGitUrl)
            .setRefSpecs(RefSpec("+$deployedRevision:refs/pf/deployed"))
            .setTimeout(TIMEOUT_SECONDS)
            .setCredentialsProvider(null)
            .setCheckFetchedObjects(true)
            .setTagOpt(TagOpt.NO_TAGS)
            .setRecurseSubmodules(FetchRecurseSubmodulesMode.NO)
            .call()
    }

    private fun validatePublicUrl(publicGitUrl: String) {
        val uri = runCatching { URI(publicGitUrl) }.getOrNull()
        if (publicGitUrl.length > 1000 || uri?.scheme != "https" || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null || uri.rawPath.isNullOrBlank()
        ) {
            throw InvalidCommand("De publieke Git-repository moet een begrensde HTTPS-URL zonder inloggegevens zijn.")
        }
    }

    companion object {
        private val SHA = Regex("[0-9a-f]{40}")
        private const val TIMEOUT_SECONDS = 30
        private const val CACHE_SIZE = 128
    }
}
