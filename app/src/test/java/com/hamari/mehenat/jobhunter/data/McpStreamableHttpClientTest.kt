package com.hamari.mehenat.jobhunter.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class McpStreamableHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: McpStreamableHttpClient

    @Before fun setUp() {
        val held = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(held).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        server = MockWebServer().apply { useHttps(serverCertificates.sslSocketFactory(), false); start() }
        val http = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
        client = McpStreamableHttpClient(server.url("/mcp").toString(), http = http)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun discoversCurrentProtocolAndListsOnlyExplicitlyReadOnlyTools() {
        enqueueModernDiscovery()
        server.enqueue(jsonResponse(2, JSONObject().put("resultType", "complete").put("tools", JSONArray().put(
            JSONObject()
                .put("name", "search_jobs")
                .put("description", "Search public listings")
                .put("annotations", JSONObject().put("readOnlyHint", true))
                .put("inputSchema", JSONObject().put("type", "object"))
        ).put(
            JSONObject().put("name", "submit_application")
                .put("annotations", JSONObject().put("readOnlyHint", false))
        ).put(
            JSONObject().put("name", "delete_record")
                .put("annotations", JSONObject().put("readOnlyHint", true).put("destructiveHint", true))
        ))))
        assertEquals("Example MCP", client.connect())
        val tools = client.listReadOnlyTools()
        assertEquals(1, tools.size)
        assertEquals("search_jobs", tools.first().name)
        assertTrue(tools.first().readOnly)
        val discoverRequest = server.takeRequest()
        assertEquals("2026-07-28", discoverRequest.getHeader("MCP-Protocol-Version"))
        assertEquals("server/discover", discoverRequest.getHeader("Mcp-Method"))
        val discoverBody = JSONObject(discoverRequest.body.readUtf8())
        assertEquals(
            "2026-07-28",
            discoverBody.getJSONObject("params").getJSONObject("_meta")
                .getString("io.modelcontextprotocol/protocolVersion")
        )
        assertEquals("tools/list", server.takeRequest().getHeader("Mcp-Method"))
    }

    @Test fun refusesToCallToolsWithoutReadOnlyAnnotation() {
        enqueueModernDiscovery()
        server.enqueue(jsonResponse(2, JSONObject().put("resultType", "complete").put("tools", JSONArray().put(
            JSONObject().put("name", "send_email")
                .put("annotations", JSONObject().put("readOnlyHint", false))
        ))))
        client.connect()
        try {
            client.callReadOnlyTool("send_email", JSONObject())
            throw AssertionError("Expected unsafe tool call to be rejected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("approved read-only"))
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun requiresHttpsAndRejectsEmbeddedCredentials() {
        try {
            McpStreamableHttpClient("http://example.com/mcp")
            throw AssertionError("Expected HTTP endpoint to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("HTTPS"))
        }
        try {
            McpStreamableHttpClient("https://user:pass@example.com/mcp")
            throw AssertionError("Expected embedded credentials to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("credentials"))
        }
    }

    @Test fun parsesMultipleSseEventsAndMultilineDataUntilMatchingResponseId() {
        enqueueModernDiscovery()
        // Keep this wire fixture literal: SSE transports one logical JSON
        // message across multiple data fields, which the client must reassemble.
        val sseBody = """
            data: {"jsonrpc":"2.0","method":"notifications/progress","params":{}}

            data: {"jsonrpc":"2.0",
            data: "id":2,
            data: "result":{"resultType":"complete",
            data: "tools":[]}}
            
        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setChunkedBody(sseBody, 64)
        )
        client.connect()
        assertTrue(client.listReadOnlyTools().isEmpty())
    }

    @Test fun rejectsJsonRpcResponsesWithMismatchedIds() {
        enqueueModernDiscovery()
        server.enqueue(jsonResponse(99, JSONObject().put("resultType", "complete").put("tools", JSONArray())))
        client.connect()
        try {
            client.listReadOnlyTools()
            throw AssertionError("Expected mismatched JSON-RPC response ID to be rejected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("ID"))
        }
    }

    @Test fun rejectsChunkedResponseBodiesLargerThanOneMegabyte() {
        enqueueModernDiscovery()
        val oversized = "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"resultType\":\"complete\",\"data\":\"" +
            "x".repeat(1_048_600) + "\"}}"
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setChunkedBody(oversized, 8192))
        client.connect()
        try {
            client.listReadOnlyTools()
            throw AssertionError("Expected oversized chunked body to be rejected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("1 MB"))
        }
    }

    @Test fun fallsBackToLegacyHandshakeWhenModernEndpointIsNotSupported() {
        enqueueLegacyFallback()
        assertEquals("Legacy example", client.connect())
        val probe = server.takeRequest()
        assertEquals("server/discover", probe.getHeader("Mcp-Method"))
        val initialize = server.takeRequest()
        assertEquals("initialize", JSONObject(initialize.body.readUtf8()).getString("method"))
        assertEquals(null, initialize.getHeader("Mcp-Method"))
        val notification = server.takeRequest()
        assertEquals("2025-11-25", notification.getHeader("MCP-Protocol-Version"))
    }

    @Test fun negotiatesAdvertisedLegacyVersionFromModernVersionError() {
        val unsupported = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("error", JSONObject()
                .put("code", -32022)
                .put("message", "Unsupported protocol version")
                .put("data", JSONObject()
                    .put("supported", JSONArray().put("2025-11-25"))
                    .put("requested", "2026-07-28")))
        server.enqueue(MockResponse().setResponseCode(400)
            .setHeader("Content-Type", "application/json")
            .setBody(unsupported.toString()))
        enqueueLegacyInitializeOnly()
        assertEquals("Legacy example", client.connect())
    }

    @Test fun paginatesToolDiscoveryAndReturnsOnlySafeAdvertisedTools() {
        enqueueModernDiscovery()
        server.enqueue(jsonResponse(2, JSONObject().put("resultType", "complete")
            .put("tools", JSONArray().put(
                JSONObject().put("name", "search_jobs")
                    .put("annotations", JSONObject().put("readOnlyHint", true))
            )).put("nextCursor", "page-two")))
        server.enqueue(jsonResponse(3, JSONObject().put("resultType", "complete")
            .put("tools", JSONArray().put(
                JSONObject().put("name", "read_company")
                    .put("annotations", JSONObject().put("readOnlyHint", true))
            ).put(
                JSONObject().put("name", "send_email")
                    .put("annotations", JSONObject().put("readOnlyHint", false))
            ))))
        client.connect()
        val tools = client.listReadOnlyTools()
        assertEquals(listOf("search_jobs", "read_company"), tools.map { it.name })
    }

    @Test fun sendsRequiredStandardAndCustomParameterHeadersForModernToolCalls() {
        enqueueModernDiscovery()
        val tool = JSONObject()
            .put("name", "lookup_company")
            .put("annotations", JSONObject().put("readOnlyHint", true))
            .put("inputSchema", JSONObject().put("type", "object")
                .put("properties", JSONObject()
                    .put("region", JSONObject().put("type", "string").put("x-mcp-header", "Region"))
                    .put("query", JSONObject().put("type", "string"))))
        server.enqueue(jsonResponse(2, JSONObject().put("resultType", "complete")
            .put("tools", JSONArray().put(tool))))
        server.enqueue(jsonResponse(3, JSONObject().put("resultType", "complete")
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "Found company")))))
        client.connect()
        val result = client.callReadOnlyTool("lookup_company", JSONObject()
            .put("region", "west-1")
            .put("query", "Example Ltd"))
        assertEquals("Found company", result)
        assertEquals("server/discover", server.takeRequest().getHeader("Mcp-Method"))
        assertEquals("tools/list", server.takeRequest().getHeader("Mcp-Method"))
        val toolRequest = server.takeRequest()
        assertEquals("tools/call", toolRequest.getHeader("Mcp-Method"))
        assertEquals("lookup_company", toolRequest.getHeader("Mcp-Name"))
        assertEquals("west-1", toolRequest.getHeader("Mcp-Param-Region"))
    }

    @Test fun excludesToolsWithInvalidCustomHeaderAnnotations() {
        enqueueModernDiscovery()
        val tool = JSONObject()
            .put("name", "invalid_header")
            .put("annotations", JSONObject().put("readOnlyHint", true))
            .put("inputSchema", JSONObject().put("type", "object")
                .put("properties", JSONObject().put("items", JSONObject().put("type", "array")
                    .put("items", JSONObject().put("type", "string").put("x-mcp-header", "Region")))))
        server.enqueue(jsonResponse(2, JSONObject().put("resultType", "complete")
            .put("tools", JSONArray().put(tool))))
        client.connect()
        assertTrue(client.listReadOnlyTools().isEmpty())
    }

    private fun enqueueModernDiscovery() {
        val discoveryResult = JSONObject()
            .put("resultType", "complete")
            .put("supportedVersions", JSONArray().put("2026-07-28"))
            .put("capabilities", JSONObject().put("tools", JSONObject()))
            .put("_meta", JSONObject().put(
                "io.modelcontextprotocol/serverInfo",
                JSONObject().put("name", "Example MCP").put("version", "1.0")
            ))
            .put("ttlMs", 0)
            .put("cacheScope", "public")
        server.enqueue(jsonResponse(1, discoveryResult))
    }

    private fun enqueueLegacyFallback() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Legacy MCP endpoint"))
        enqueueLegacyInitializeOnly()
    }

    private fun enqueueLegacyInitializeOnly() {
        val result = JSONObject()
            .put("protocolVersion", "2025-11-25")
            .put("capabilities", JSONObject().put("tools", JSONObject()))
            .put("serverInfo", JSONObject().put("name", "Legacy example").put("version", "1.0"))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setHeader("Mcp-Session-Id", "session-123")
            .setBody(JSONObject().put("jsonrpc", "2.0").put("id", 2).put("result", result).toString()))
        server.enqueue(MockResponse().setResponseCode(202))
    }

    private fun jsonResponse(id: Int, result: JSONObject): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json")
            .setBody(JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString())
}
