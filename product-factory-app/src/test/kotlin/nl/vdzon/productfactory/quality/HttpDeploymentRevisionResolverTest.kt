package nl.vdzon.productfactory.quality

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import nl.vdzon.productfactory.api.shared.InvalidCommand
import nl.vdzon.productfactory.quality.mvp.HttpDeploymentRevisionResolver
import nl.vdzon.productfactory.quality.mvp.TransientTestEnvironmentFailure
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class HttpDeploymentRevisionResolverTest {
    @Test
    fun `alleen expliciet tijdelijke HTTP storing is automatisch herstelbaar`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val status = exchange.requestURI.path.removePrefix("/").toInt()
            val body = "{}".toByteArray()
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val resolver = HttpDeploymentRevisionResolver(ObjectMapper())
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            listOf(408, 429, 502, 503, 504).forEach { status ->
                assertThatThrownBy { resolver.resolve(baseUrl, "/$status", "$.commit") }
                    .describedAs("HTTP $status is een expliciete tijdelijke storing")
                    .isInstanceOf(TransientTestEnvironmentFailure::class.java)
            }
            listOf(400, 401, 403, 404, 500).forEach { status ->
                assertThatThrownBy { resolver.resolve(baseUrl, "/$status", "$.commit") }
                    .describedAs("HTTP $status bewijst geen tijdelijke storing")
                    .isInstanceOf(InvalidCommand::class.java)
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `succesvolle revision blijft geldig maar ontbrekend revisionveld vergt herstel`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = "{\"commit\":\"deployed-sha\"}".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val resolver = HttpDeploymentRevisionResolver(ObjectMapper())
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            assertThat(resolver.resolve(baseUrl, "/", "$.commit")).isEqualTo("deployed-sha")
            assertThatThrownBy { resolver.resolve(baseUrl, "/", "$.missing") }
                .isInstanceOf(InvalidCommand::class.java)
        } finally {
            server.stop(0)
        }
    }
}
