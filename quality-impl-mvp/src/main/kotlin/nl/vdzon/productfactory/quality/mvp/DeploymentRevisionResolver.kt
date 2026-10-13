package nl.vdzon.productfactory.quality.mvp

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.foundation.DeploymentRevisionResolver
import nl.vdzon.productfactory.api.shared.InvalidCommand
import org.springframework.stereotype.Component
import java.net.URI
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

@Component
class HttpDeploymentRevisionResolver(
    private val mapper: ObjectMapper,
) : DeploymentRevisionResolver {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()

    override fun resolve(baseUrl: String, revisionEndpoint: String, revisionJsonPath: String): String {
        val base = URI.create(baseUrl)
        val endpoint = base.resolve(revisionEndpoint)
        if (endpoint.scheme !in setOf("http", "https") || endpoint.host != base.host) {
            throw InvalidCommand("Revisionendpoint valt buiten de geconfigureerde producthost.")
        }
        val response = try {
            client.send(
                HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        } catch (error: java.io.IOException) {
            if (error is HttpTimeoutException || error is SocketTimeoutException || error is ConnectException) {
                throw TransientTestEnvironmentFailure("Revisionendpoint is tijdelijk niet bereikbaar.", error)
            }
            throw error
        }
        if (response.statusCode() in setOf(408, 429, 502, 503, 504)) {
            throw TransientTestEnvironmentFailure("Revisionendpoint heeft een tijdelijke technische storing (HTTP ${response.statusCode()}).")
        }
        if (response.statusCode() !in 200..299 || response.body().length > 100_000) throw InvalidCommand("Revisionendpoint is niet veilig bereikbaar.")
        var node = mapper.readTree(response.body())
        revisionJsonPath.removePrefix("$").trimStart('.').split('.').filter(String::isNotBlank).forEach { node = node.path(it) }
        return node.takeIf { it.isTextual }?.asText()?.trim()?.takeIf(String::isNotBlank)
            ?: throw InvalidCommand("Revisionendpoint bevat de geconfigureerde revisie niet.")
    }
}

/** Expliciet tijdelijk transportfalen; ontbrekende routes, toegang en configuratie vallen hier niet onder. */
class TransientTestEnvironmentFailure(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
