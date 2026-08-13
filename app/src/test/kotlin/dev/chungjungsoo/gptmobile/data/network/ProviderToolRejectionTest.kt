package dev.chungjungsoo.gptmobile.data.network

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.agent.ToolDefinitionsRejectedException
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.TextContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.AnthropicTool
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import dev.chungjungsoo.gptmobile.data.dto.google.common.Part
import dev.chungjungsoo.gptmobile.data.dto.google.common.Role as GoogleRole
import dev.chungjungsoo.gptmobile.data.dto.google.request.FunctionDeclaration
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleTool
import dev.chungjungsoo.gptmobile.data.dto.groq.request.GroqChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent as OpenAITextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatFunctionTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseFunctionTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import io.ktor.client.engine.cio.CIO
import java.net.InetSocketAddress
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderToolRejectionTest {
    private val schema = buildJsonObject { put("type", "object") }

    @Test
    fun `openai responses rejects tool definitions before emitting provider events`() = withServer(400, openAIError()) { baseUrl ->
        val api = OpenAIAPIImpl(NetworkClient(CIO))

        assertThrows(ToolDefinitionsRejectedException::class.java) {
            runBlocking {
                api.streamResponses(responsesRequest(withTools = true), 5, config(baseUrl)).toList()
            }
        }
        val event = runBlocking {
            api.streamResponses(responsesRequest(withTools = false), 5, config(baseUrl)).single()
        }

        assertEquals("400", (event as ResponseErrorEvent).code)
    }

    @Test
    fun `openai compatible rejects tool definitions before emitting provider events`() = withServer(422, openAIError()) { baseUrl ->
        val api = OpenAIAPIImpl(NetworkClient(CIO))

        assertThrows(ToolDefinitionsRejectedException::class.java) {
            runBlocking {
                api.streamChatCompletion(chatRequest(withTools = true), 5, config(baseUrl)).toList()
            }
        }
        val chunk = runBlocking {
            api.streamChatCompletion(chatRequest(withTools = false), 5, config(baseUrl)).single()
        }

        assertEquals("422", chunk.error?.code)
    }

    @Test
    fun `groq rejects tool definitions before emitting provider events`() = withServer(400, openAIError()) { baseUrl ->
        val api = GroqAPIImpl(NetworkClient(CIO))

        assertThrows(ToolDefinitionsRejectedException::class.java) {
            runBlocking {
                api.streamChatCompletion(groqRequest(withTools = true), 5, config(baseUrl)).toList()
            }
        }
        val chunk = runBlocking {
            api.streamChatCompletion(groqRequest(withTools = false), 5, config(baseUrl)).single()
        }

        assertEquals("400", chunk.error?.code)
    }

    @Test
    fun `anthropic rejects tool definitions before emitting provider events`() = withServer(422, anthropicError()) { baseUrl ->
        val api = AnthropicAPIImpl(NetworkClient(CIO))

        assertThrows(ToolDefinitionsRejectedException::class.java) {
            runBlocking {
                api.streamChatMessage(anthropicRequest(withTools = true), 5, config(baseUrl)).toList()
            }
        }
        val chunk = runBlocking {
            api.streamChatMessage(anthropicRequest(withTools = false), 5, config(baseUrl)).single()
        }

        assertEquals("invalid request", (chunk as ErrorResponseChunk).error.message)
    }

    @Test
    fun `gemini rejects tool definitions before emitting provider events`() = withServer(400, googleError()) { baseUrl ->
        val api = GoogleAPIImpl(NetworkClient(CIO))

        assertThrows(ToolDefinitionsRejectedException::class.java) {
            runBlocking {
                api.streamGenerateContent(geminiRequest(withTools = true), "model", 5, config(baseUrl)).toList()
            }
        }
        val response = runBlocking {
            api.streamGenerateContent(geminiRequest(withTools = false), "model", 5, config(baseUrl)).single()
        }

        assertEquals(400, response.error?.code)
    }

    @Test
    fun `gemini sends api key only in a header`() {
        var apiKeyHeader: String? = null
        var query: String? = null
        withServer(
            status = 400,
            body = googleError(),
            onRequest = { exchange ->
                apiKeyHeader = exchange.requestHeaders.getFirst("x-goog-api-key")
                query = exchange.requestURI.rawQuery
            }
        ) { baseUrl ->
            runBlocking {
                GoogleAPIImpl(NetworkClient(CIO))
                    .streamGenerateContent(geminiRequest(withTools = false), "model", 5, config(baseUrl))
                    .single()
            }
        }

        assertEquals("token", apiKeyHeader)
        assertFalse(query.orEmpty().split('&').any { it.startsWith("key=") })
    }

    private fun responsesRequest(withTools: Boolean) = ResponsesRequest(
        model = "model",
        input = listOf(ResponseInputMessage("user", ResponseInputContent.text("hello"))),
        tools = toolList(withTools) { ResponseFunctionTool("lookup", "Lookup", schema) }
    )

    private fun chatRequest(withTools: Boolean) = ChatCompletionRequest(
        model = "model",
        messages = chatMessages(),
        tools = toolList(withTools) { ChatFunctionTool("lookup", "Lookup", schema) }
    )

    private fun groqRequest(withTools: Boolean) = GroqChatCompletionRequest(
        model = "model",
        messages = chatMessages(),
        tools = toolList(withTools) { ChatFunctionTool("lookup", "Lookup", schema) }
    )

    private fun anthropicRequest(withTools: Boolean) = MessageRequest(
        model = "model",
        messages = listOf(InputMessage(MessageRole.USER, listOf(TextContent("hello")))),
        maxTokens = 16,
        tools = toolList(withTools) { AnthropicTool("lookup", "Lookup", schema) }
    )

    private fun geminiRequest(withTools: Boolean) = GenerateContentRequest(
        contents = listOf(Content(GoogleRole.USER, listOf(Part.text("hello")))),
        tools = toolList(withTools) {
            GoogleTool(listOf(FunctionDeclaration("lookup", "Lookup", schema)))
        }
    )

    private fun chatMessages() = listOf(
        ChatMessage(Role.USER, listOf(OpenAITextContent("hello")))
    )

    private fun config(baseUrl: String) = ProviderRequestConfig(baseUrl, "token")

    private fun <T> toolList(withTools: Boolean, create: () -> T): List<T>? = if (withTools) listOf(create()) else null

    private fun <T> withServer(
        status: Int,
        body: String,
        onRequest: (HttpExchange) -> Unit = {},
        block: (String) -> T
    ): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            onRequest(exchange)
            exchange.requestBody.close()
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return try {
            block("http://127.0.0.1:${server.address.port}/")
        } finally {
            server.stop(0)
        }
    }

    private fun openAIError() = """{"error":{"message":"invalid tools"}}"""

    private fun anthropicError() = """{"type":"error","error":{"type":"invalid_request_error","message":"invalid request"}}"""

    private fun googleError() = """{"error":{"code":400,"message":"invalid tools","status":"INVALID_ARGUMENT"}}"""
}
