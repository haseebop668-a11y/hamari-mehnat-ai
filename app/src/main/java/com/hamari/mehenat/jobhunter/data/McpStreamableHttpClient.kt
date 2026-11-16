package com.hamari.mehenat.jobhunter.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class McpToolDescriptor(
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
    val readOnly: Boolean
)

/**
 * Minimal MCP Streamable HTTP client.
 *
 * Security boundary: only HTTPS endpoints are accepted; remote code is never
 * downloaded or executed. Tool calls are restricted to tools explicitly marked
 * readOnlyHint=true and not marked destructiveHint=true. The user must initiate
 * every call. This transport does not pretend to provide provider OAuth.
 */
class McpStreamableHttpClient(
    private val endpoint: String,
    private val bearerToken: String? = null,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    private val ids = AtomicInteger(1)
    private var sessionId: String? = null
    private var protocolVersion = "2025-03-26"
    private var initialized = false

    init {
        val parsed = java.net.URI(endpoint)
        require(parsed.scheme.equals("https", true)) { "MCP server must use HTTPS." }
        require(parsed.host != null && parsed.userInfo == null) { "Enter a valid HTTPS MCP endpoint without embedded credentials." }
        require(endpoint.length <= 2048) { "MCP endpoint is too long." }
        require(bearerToken == null || bearerToken.length <= 4096) { "Authorization token is too long." }
    }

    @Synchronized
    fun connect(): String {
        check(!initialized) { "This MCP session is already connected." }
        val response = request(
            "initialize",
            JSONObject()
                .put("protocolVersion", protocolVersion)
                .put("capabilities", JSONObject())
                .put("clientInfo", JSONObject().put("name", "Hamari Mehnat").put("version", "1.0.0"))
        )
        val result = response.getJSONObject("result")
        val negotiated = result.optString("protocolVersion")
        check(negotiated.isNotBlank()) { "MCP server did not negotiate a protocol version." }
        protocolVersion = negotiated
        initialized = true
        notifyInitialized()
        val info = result.optJSONObject("serverInfo")
        return info?.optString("name").orEmpty().ifBlank { "MCP server connected" }
    }

    @Synchronized
    fun listReadOnlyTools(): List<McpToolDescriptor> {
        check(initialized) { "Connect to the MCP server first." }
        val result = request("tools/list", JSONObject()).getJSONObject("result")
        val tools = result.optJSONArray("tools") ?: JSONArray()
        return (0 until tools.length()).mapNotNull { index ->
            val item = tools.optJSONObject(index) ?: return@mapNotNull null
            val annotations = item.optJSONObject("annotations") ?: JSONObject()
            val readOnly = annotations.optBoolean("readOnlyHint", false) &&
                !annotations.optBoolean("destructiveHint", false)
            if (!readOnly) return@mapNotNull null
            val name = item.optString("name").takeIf { it.matches(Regex("[A-Za-z0-9_.-]{1,128}")) }
                ?: return@mapNotNull null
            McpToolDescriptor(
                name = name,
                description = item.optString("description").take(1000),
                inputSchema = item.optJSONObject("inputSchema") ?: JSONObject().put("type", "object"),
                readOnly = true
            )
        }.take(50)
    }

    @Synchronized
    fun callReadOnlyTool(name: String, arguments: JSONObject): String {
        check(initialized) { "Connect to the MCP server first." }
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,128}"))) { "Invalid MCP tool name." }
        require(arguments.toString().length <= 16_384) { "MCP tool arguments are too large." }
        val available = listReadOnlyTools()
        check(available.any { it.name == name }) { "This tool is not in the approved read-only tool list." }
        val result = request(
            "tools/call",
            JSONObject().put("name", name).put("arguments", arguments)
        ).getJSONObject("result")
        check(!result.optBoolean("isError", false)) {
            result.optJSONArray("content")?.let { array ->
                (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("text") }.joinToString("\n")
            }?.take(500) ?: "MCP tool returned an error."
        }
        val content = result.optJSONArray("content") ?: JSONArray()
        val text = (0 until content.length()).mapNotNull { index ->
            content.optJSONObject(index)?.optString("text")?.takeIf(String::isNotBlank)
        }.joinToString("\n")
        return text.ifBlank { result.toString() }.take(12_000)
    }

    private fun notifyInitialized() {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", "notifications/initialized")
        execute(body, allowEmpty = true)
    }

    private fun request(method: String, params: JSONObject): JSONObject {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", ids.getAndIncrement())
            .put("method", method)
            .put("params", params)
        return execute(body, allowEmpty = false)
    }

    private fun execute(body: JSONObject, allowEmpty: Boolean): JSONObject {
        val builder = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json, text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        sessionId?.let { builder.header("Mcp-Session-Id", it) }
        if (initialized) builder.header("MCP-Protocol-Version", protocolVersion)
        bearerToken?.takeIf(String::isNotBlank)?.let { builder.header("Authorization", "Bearer $it") }
        http.newCall(builder.build()).execute().use { response ->
            check(response.code != 301 && response.code != 302 && response.code != 307 && response.code != 308) {
                "MCP endpoint redirected. Use the final HTTPS endpoint directly."
            }
            check(response.isSuccessful) { "MCP HTTP request failed: HTTP ${response.code}" }
            response.header("Mcp-Session-Id")?.let { sessionId = it }
            val raw = response.body?.string().orEmpty()
            if (raw.isBlank() && allowEmpty) return JSONObject()
            check(raw.length <= MAX_RESPONSE_CHARS) { "MCP response exceeded the 1 MB safety limit." }
            val jsonText = if (response.header("Content-Type").orEmpty().contains("text/event-stream", true)) {
                raw.lineSequence().filter { it.startsWith("data:") }
                    .map { it.removePrefix("data:").trim() }
                    .filter(String::isNotBlank)
                    .lastOrNull() ?: error("MCP server returned an empty event stream.")
            } else raw
            val parsed = JSONObject(jsonText)
            parsed.optJSONObject("error")?.let { error ->
                throw IllegalStateException("MCP error ${error.optString("code")}: ${error.optString("message")}")
            }
            return parsed
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_RESPONSE_CHARS = 1_048_576
    }
}
