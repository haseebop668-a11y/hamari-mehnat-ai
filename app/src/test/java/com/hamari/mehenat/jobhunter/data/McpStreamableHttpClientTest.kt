package com.hamari.mehenat.jobhunter.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun initializesAndListsOnlyExplicitlyReadOnlyTools() {
        enqueueInitialize()
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[
                {"name":"search_jobs","description":"Search public listings","annotations":{"readOnlyHint":true},"inputSchema":{"type":"object"}},
                {"name":"submit_application","annotations":{"readOnlyHint":false}},
                {"name":"delete_record","annotations":{"readOnlyHint":true,"destructiveHint":true}}
            ]}}"""))
        assertEquals("Example MCP", client.connect())
        val tools = client.listReadOnlyTools()
        assertEquals(1, tools.size)
        assertEquals("search_jobs", tools.first().name)
        assertTrue(tools.first().readOnly)
    }

    @Test fun refusesToCallToolsWithoutReadOnlyAnnotation() {
        enqueueInitialize()
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"send_email","annotations":{"readOnlyHint":false}}]}}"""))
        client.connect()
        try {
            client.callReadOnlyTool("send_email", JSONObject())
            throw AssertionError("Expected unsafe tool call to be rejected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("approved read-only"))
        }
        assertEquals(3, server.requestCount)
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

    private fun enqueueInitialize() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setHeader("Mcp-Session-Id", "session-123")
            .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26","capabilities":{"tools":{}},"serverInfo":{"name":"Example MCP","version":"1.0"}}}"""))
    }
}
