package dev.chungjungsoo.gptmobile.data.agent.tool

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class McpClientManagerTest {
    @Test
    fun `reuses initialized session for discovery and SSE tool call`() = runBlocking {
        McpFixtureServer().use { server ->
            val client = testClient()
            val manager = McpClientManager(client)
            val config = McpConnectionConfig(
                connectionUid = "connection-1",
                endpointUrl = server.url,
                allowCleartext = true,
                authorizationHeader = "Bearer test-token"
            )

            val tools = manager.listTools(config)
            val result = manager.callTool(config, "echo", buildJsonObject { put("text", "hello") })

            assertEquals(listOf("echo"), tools.map { it.name })
            assertEquals("hello", result.content.single().let { it as io.modelcontextprotocol.kotlin.sdk.types.TextContent }.text)
            assertEquals(1, server.methods.count { it == "initialize" })
            assertTrue(server.sessionHeaders.filterNotNull().all { it == "session-1" })
            assertTrue(server.authorizationHeaders.all { it == "Bearer test-token" })
            manager.closeAll()
            client.close()
        }
    }

    @Test
    fun `rejects cleartext endpoint until user allowed it`() = runBlocking {
        val client = testClient()
        val manager = McpClientManager(client)

        try {
            manager.listTools(
                McpConnectionConfig(
                    connectionUid = "connection-1",
                    endpointUrl = "http://127.0.0.1:8080/mcp",
                    allowCleartext = false
                )
            )
            fail("Expected a cleartext validation failure")
        } catch (_: IllegalArgumentException) {
        }
        client.close()
    }

    @Test
    fun `follows tool list cursors without reinitializing session`() = runBlocking {
        McpFixtureServer(paginateTools = true).use { server ->
            val client = testClient()
            val manager = McpClientManager(client)
            val config = McpConnectionConfig("connection-1", server.url, allowCleartext = true)

            val tools = manager.listTools(config)

            assertEquals(listOf("echo", "second"), tools.map { it.name })
            assertEquals(2, server.methods.count { it == "tools/list" })
            assertEquals(1, server.methods.count { it == "initialize" })
            manager.closeAll()
            client.close()
        }
    }

    private fun testClient(): HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) { json(JSON) }
        install(SSE)
    }

    internal class McpFixtureServer(
        private val paginateTools: Boolean = false,
        acceptedAuthorization: String? = null
    ) : AutoCloseable {
        @Volatile
        var acceptedAuthorization: String? = acceptedAuthorization
        val methods = CopyOnWriteArrayList<String>()
        val sessionHeaders = CopyOnWriteArrayList<String?>()
        val authorizationHeaders = CopyOnWriteArrayList<String>()
        val refreshRequests = AtomicInteger()
        private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/mcp", ::handle)
            createContext("/token", ::token)
            start()
        }
        val url: String = "http://127.0.0.1:${server.address.port}/mcp"
        val tokenUrl: String = "http://127.0.0.1:${server.address.port}/token"

        private fun handle(exchange: HttpExchange) {
            try {
                val authorization = exchange.requestHeaders.getFirst("Authorization")
                authorization?.let(authorizationHeaders::add)
                exchange.requestHeaders.getFirst("Mcp-Session-Id")?.let(sessionHeaders::add)
                if (acceptedAuthorization != null && authorization != acceptedAuthorization) {
                    exchange.sendResponseHeaders(401, -1)
                    return
                }
                val request = JSON.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).let { it as JsonObject }
                val method = request["method"]?.jsonPrimitive?.content.orEmpty()
                methods += method

                if (method == "notifications/initialized") {
                    exchange.sendResponseHeaders(202, -1)
                    return
                }

                val result = when (method) {
                    "initialize" -> """{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"fixture","version":"1"}}"""

                    "tools/list" -> {
                        val cursor = (request["params"] as? JsonObject)?.get("cursor")?.jsonPrimitive?.content
                        when {
                            !paginateTools -> toolList("echo")
                            cursor == null -> toolList("echo", nextCursor = "page-2")
                            else -> toolList("second")
                        }
                    }

                    "tools/call" -> {
                        val text = request["params"]!!.let { it as JsonObject }["arguments"]!!.let { it as JsonObject }["text"]!!.jsonPrimitive.content
                        """{"content":[{"type":"text","text":"$text"}]}"""
                    }

                    else -> error("Unexpected MCP method: $method")
                }
                val response = """{"jsonrpc":"2.0","id":${request["id"] ?: JsonNull},"result":$result}"""
                if (method == "initialize") exchange.responseHeaders.add("Mcp-Session-Id", "session-1")
                if (method == "tools/call") {
                    respond(exchange, "text/event-stream", "event: message\ndata: $response\n\n")
                } else {
                    respond(exchange, "application/json", response)
                }
            } finally {
                exchange.close()
            }
        }

        private fun token(exchange: HttpExchange) {
            exchange.requestBody.bufferedReader().readText()
            refreshRequests.incrementAndGet()
            respond(
                exchange,
                "application/json",
                """{"access_token":"access-2","token_type":"Bearer","refresh_token":"refresh-2","expires_in":120}"""
            )
            exchange.close()
        }

        override fun close() {
            server.stop(0)
        }

        private fun respond(exchange: HttpExchange, contentType: String, body: String) {
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        private fun toolList(name: String, nextCursor: String? = null): String = """{"tools":[{"name":"$name","description":"Echo text","inputSchema":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}}]${nextCursor?.let { ",\"nextCursor\":\"$it\"" }.orEmpty()}}"""
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
