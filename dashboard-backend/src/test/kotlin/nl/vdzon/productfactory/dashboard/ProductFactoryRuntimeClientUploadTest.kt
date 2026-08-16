package nl.vdzon.productfactory.dashboard

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductFactoryRuntimeClientUploadTest {
    @Test
    fun `upload proxy sends image and alt text as multipart request`() {
        val requestPath = AtomicReference<String>()
        val requestContentType = AtomicReference<String>()
        val requestBody = AtomicReference<ByteArray>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/products/hkh-autopilot/media") { exchange ->
            requestPath.set(exchange.requestURI.path)
            requestContentType.set(exchange.requestHeaders.getFirst("Content-Type"))
            requestBody.set(exchange.requestBody.use { it.readAllBytes() })
            val response = """{"id":"media-1"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val client = ProductFactoryRuntimeClient("http://127.0.0.1:${server.address.port}")
            val result = client.uploadProductMedia(
                slug = "hkh-autopilot",
                filename = "epics.png",
                contentType = "image/png",
                bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
                altText = "Screenshot van de epics",
            ) as Map<*, *>

            assertEquals("media-1", result["id"])
            assertEquals("/api/products/hkh-autopilot/media", requestPath.get())
            assertTrue(requestContentType.get().startsWith("multipart/form-data;boundary="))
            val multipart = requestBody.get().toString(StandardCharsets.ISO_8859_1)
            assertTrue(multipart.contains("name=\"file\""))
            assertTrue(multipart.contains("filename=\"epics.png\""))
            assertTrue(multipart.contains("Content-Type: image/png"))
            assertTrue(multipart.contains("name=\"altText\""))
            assertTrue(multipart.contains("Screenshot van de epics"))
        } finally {
            server.stop(0)
        }
    }
}
