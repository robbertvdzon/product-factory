package nl.vdzon.productfactory.workspace

import nl.vdzon.productfactory.contracts.WorkspacePublicationView
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64

data class PublishArtifactRequest(val runId: String, val productSlug: String, val relativePath: String, val content: String)

internal fun gitAuthorizationHeader(token: String): String {
    val credentials = Base64.getEncoder().encodeToString("x-access-token:$token".toByteArray())
    return "Authorization: Basic $credentials"
}

class WorkspaceRepositoryGuard(private val configuredRepository: String) {
    fun requireWorkspaceRepository(candidate: String) {
        val canonical = candidate.removeSuffix("/").removeSuffix(".git")
        val expected = configuredRepository.removeSuffix("/").removeSuffix(".git")
        require(canonical == expected && canonical.endsWith("product-factory-workspace")) {
            "Publicatie is uitsluitend toegestaan naar product-factory-workspace"
        }
    }
}

@Service
class WorkspacePublisher(
    private val jdbc: JdbcTemplate,
    @Value("\${product-factory.workspace.path}") private val workspacePath: String,
    @Value("\${product-factory.workspace.repository}") private val repository: String,
    @Value("\${product-factory.workspace.main-branch:main}") private val mainBranch: String,
    @Value("\${product-factory.workspace.remote-publication:false}") private val remotePublication: Boolean,
    @Value("\${PF_WORKSPACE_GITHUB_TOKEN:}") private val workspaceToken: String,
) {
    fun publish(request: PublishArtifactRequest): WorkspacePublicationView {
        validate(request)
        val hash = sha256(request.content)
        find(request.runId)?.let { existing ->
            if (existing.contentHash != hash || existing.productSlug != request.productSlug || existing.artifactPath != request.relativePath) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Run-ID is al voor andere inhoud gebruikt")
            }
            return existing
        }

        WorkspaceRepositoryGuard(repository).requireWorkspaceRepository(readOriginOrConfigured())
        val root = Path.of(workspacePath).toAbsolutePath().normalize()
        require(Files.isDirectory(root.resolve(".git"))) { "PF_WORKSPACE_PATH moet een Git-checkout zijn" }
        val artifact = root.resolve("products").resolve(request.productSlug).resolve(request.relativePath).normalize()
        require(artifact.startsWith(root.resolve("products").resolve(request.productSlug))) { "Artefactpad verlaat productdirectory" }

        git(root, "checkout", mainBranch)
        if (remotePublication) git(root, "pull", "--ff-only", "origin", mainBranch)
        val branch = "product-factory/${request.productSlug}/${safe(request.runId)}"
        git(root, "checkout", "-B", branch, mainBranch)
        Files.createDirectories(artifact.parent)
        Files.writeString(artifact, request.content)
        git(root, "add", root.relativize(artifact).toString())
        git(root, "-c", "user.name=Product Factory", "-c", "user.email=product-factory@vdzonsoftware.nl", "commit", "-m", "product(${request.productSlug}): publish ${request.runId}")
        val commitSha = git(root, "rev-parse", "HEAD").trim()
        var pullRequest: String? = null
        var status = "COMMITTED_LOCAL"
        if (remotePublication) {
            require(workspaceToken.isNotBlank()) { "PF_WORKSPACE_GITHUB_TOKEN ontbreekt" }
            command(root, listOf("git", "push", "--force-with-lease", "-u", "origin", branch), true)
            pullRequest = createPullRequest(branch, request.runId)
            status = "PULL_REQUEST"
        }
        jdbc.update("insert into workspace_publication(run_id, product_slug, artifact_path, content_hash, status, pull_request_url, commit_sha) values (?, ?, ?, ?, ?, ?, ?)", request.runId, request.productSlug, request.relativePath, hash, status, pullRequest, commitSha)
        return find(request.runId)!!
    }

    fun find(runId: String): WorkspacePublicationView? = jdbc.query(
        "select run_id, product_slug, artifact_path, content_hash, status, pull_request_url, commit_sha from workspace_publication where run_id = ?",
        { row, _ -> WorkspacePublicationView(row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5), row.getString(6), row.getString(7)) }, runId
    ).firstOrNull()

    fun list(): List<WorkspacePublicationView> = jdbc.query(
        "select run_id, product_slug, artifact_path, content_hash, status, pull_request_url, commit_sha from workspace_publication order by created_at desc",
    ) { row, _ -> WorkspacePublicationView(row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5), row.getString(6), row.getString(7)) }

    fun readArtifact(runId: String): String {
        val publication = find(runId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val path = Path.of(workspacePath).resolve("products").resolve(publication.productSlug).resolve(publication.artifactPath).normalize()
        return Files.readString(path)
    }

    private fun validate(request: PublishArtifactRequest) {
        require(request.runId.matches(Regex("[A-Za-z0-9._-]{1,120}"))) { "Ongeldig run-ID" }
        require(request.productSlug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "Ongeldige productslug" }
        require(request.relativePath.endsWith(".md") && !Path.of(request.relativePath).isAbsolute) { "Alleen relatieve Markdown-artefacten zijn toegestaan" }
        require(request.content.isNotBlank()) { "Lege artefacten zijn niet toegestaan" }
    }

    private fun readOriginOrConfigured(): String = runCatching { git(Path.of(workspacePath), "remote", "get-url", "origin").trim() }.getOrDefault(repository)
    private fun git(directory: Path, vararg args: String) = command(directory, listOf("git") + args)
    private fun command(directory: Path, args: List<String>, tokenRequired: Boolean = false): String {
        val process = ProcessBuilder(args).directory(directory.toFile()).redirectErrorStream(true)
        process.environment().remove("GH_TOKEN")
        process.environment().remove("GITHUB_TOKEN")
        if (tokenRequired) {
            process.environment()["GIT_CONFIG_COUNT"] = "1"
            process.environment()["GIT_CONFIG_KEY_0"] = "http.extraHeader"
            process.environment()["GIT_CONFIG_VALUE_0"] = gitAuthorizationHeader(workspaceToken)
        }
        val running = process.start()
        val output = running.inputStream.bufferedReader().readText()
        check(running.waitFor() == 0) { "Workspace-opdracht mislukt: ${args.take(2).joinToString(" ")}: ${output.take(500)}" }
        return output
    }

    private fun safe(value: String) = value.lowercase().replace(Regex("[^a-z0-9._-]"), "-")
    private fun sha256(content: String) = MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun createPullRequest(branch: String, runId: String): String {
        val repositoryPath = repository.removeSuffix(".git").substringAfter("github.com:", repository.removeSuffix(".git").substringAfter("github.com/"))
        require(repositoryPath.count { it == '/' } == 1) { "Ongeldige GitHub-workspacerepository" }
        val client = RestClient.builder().baseUrl("https://api.github.com").defaultHeader("Authorization", "Bearer $workspaceToken").defaultHeader("Accept", "application/vnd.github+json").build()
        val response = client.post().uri("/repos/$repositoryPath/pulls").body(mapOf(
            "title" to "Product Factory · $runId", "head" to branch, "base" to mainBranch,
            "body" to "Goedgekeurd Product Factory-artefact voor run `$runId`."
        )).retrieve().body(Map::class.java) ?: error("GitHub gaf geen pull-requestresultaat")
        val url = response["html_url"]?.toString() ?: error("Pull-request-URL ontbreekt")
        val nodeId = response["node_id"]?.toString() ?: error("Pull-request-node ontbreekt")
        client.post().uri("/graphql").body(mapOf("query" to "mutation { enablePullRequestAutoMerge(input: {pullRequestId: \\\"$nodeId\\\", mergeMethod: SQUASH}) { pullRequest { id } } }"))
            .retrieve().toBodilessEntity()
        return url
    }
}

@RestController
@RequestMapping("/api/workspace/publications")
class WorkspacePublicationController(private val publisher: WorkspacePublisher) {
    @PostMapping @ResponseStatus(HttpStatus.CREATED) fun publish(@RequestBody request: PublishArtifactRequest) = publisher.publish(request)
    @GetMapping fun list() = publisher.list()
    @GetMapping("/{runId}") fun get(@PathVariable runId: String) = publisher.find(runId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    @GetMapping("/{runId}/artifact", produces = ["text/markdown"]) fun artifact(@PathVariable runId: String) = publisher.readArtifact(runId)
}
