package nl.vdzon.productfactory.design.mvp

import nl.vdzon.productfactory.api.shared.InvalidCommand
import org.eclipse.jgit.api.Git
import org.springframework.stereotype.Component

interface PublicGitRevisionResolver {
    fun resolveHead(publicGitUrl: String): String
}

@Component
class JGitPublicGitRevisionResolver : PublicGitRevisionResolver {
    override fun resolveHead(publicGitUrl: String): String {
        if (!publicGitUrl.startsWith("https://") || publicGitUrl.length > 1000) {
            throw InvalidCommand("De publieke Git-repository moet een begrensde HTTPS-URL zijn.")
        }
        return runCatching {
            Git.lsRemoteRepository().setRemote(publicGitUrl).call()
                .singleOrNull { it.name == "HEAD" }?.objectId?.name
        }.getOrNull()?.takeIf { SHA.matches(it) }
            ?: throw InvalidCommand("De publieke Git-HEAD kon niet naar een exacte commit-SHA worden opgelost.")
    }

    companion object {
        private val SHA = Regex("[0-9a-f]{40}")
    }
}
