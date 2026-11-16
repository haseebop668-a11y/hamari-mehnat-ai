package com.hamari.mehenat.jobhunter.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
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
    private var protocolVersion = SUPPORTED_PROTOCOL_VERSION
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
        check(negotiated == SUPPORTED_PROTOCOL_VERSION) {
            "MCP server selected unsupported protocol version: ${negotiated.ifBlank { "missing" }}."
        }
        protocolVersion = negotiated
        initialized = true
        notifyInitialized()
        val info = result.optJSONObject("serverInfo")
        return info?.optString("name").orEmpty().ifBlank { "MCP server connected" }
    }

    @Synchronized
    fun disconnect() {
        val activeSession = sessionId
        if (activeSession != null) {
            val builder = Request.Builder()
                .url(endpoint)
                .header("Mcp-Session-Id", activeSession)
                .header("MCP-Protocol-Version", protocolVersion)
                .delete()
            bearerToken?.takeIf(String::isNotBlank)?.let { builder.header("Authorization", "Bearer $it") }
            http.newCall(builder.build()).execute().use { response ->
                check(response.code in setOf(200, 202, 204, 404, 405)) {
                    "MCP session close failed: HTTP ${response.code}"
                }
            }
        }
        sessionId = null
        initialized = false
    }

    @Synchronized
    fun listReadOnlyTools(): List<McpToolDescriptor> {
        check(initialized) { "Connect to the MCP server first." }
        val discovered = linkedMapOf<String, McpToolDescriptor>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null

        for (page in 0 until MAX_TOOL_LIST_PAGES) {
            val params = JSONObject()
            cursor?.let { params.put("cursor", it) }
            val result = request("tools/list", params).getJSONObject("result")
            val tools = result.optJSONArray("tools") ?: JSONArray()
            for (index in 0 until tools.length()) {
                val item = tools.optJSONObject(index) ?: continue
                val annotations = item.optJSONObject("annotations") ?: JSONObject()
                val readOnly = annotations.optBoolean("readOnlyHint", false) &&
                    !annotations.optBoolean("destructiveHint", false)
                if (!readOnly) continue
                val name = item.optString("name").takeIf {
                    it.matches(Regex("[A-Za-z0-9_.-]{1,128}"))
                } ?: continue
                discovered.putIfAbsent(
                    name,
                    McpToolDescriptor(
                        name = name,
                        description = item.optString("description").take(1000),
                        inputSchema = item.optJSONObject("inputSchema") ?: JSONObject().put("type", "object"),
                        readOnly = true
                    )
                )
                if (discovered.size >= MAX_TOOLS) return discovered.values.toList()
            }

            val nextCursor = result.opt("nextCursor") as? String
            if (nextCursor.isNullOrBlank()) return discovered.values.toList()
            check(seenCursors.add(nextCursor)) { "MCP tools/list returned a repeated pagination cursor." }
            cursor = nextCursor
        }
        check(cursor == null) { "MCP tools/list exceeded the pagination safety limit." }
        return discovered.values.toList()
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
        bearerToken?.takeIf(String::isNotBlank)?.let { builder.header("Authorization", "Bearer " + it) }
        http.newCall(builder.build()).execute().use { response ->
            check(response.code != 301 && response.code != 302 && response.code != 307 && response.code != 308) {
                "MCP endpoint redirected. Use the final HTTPS endpoint directly."
            }
            check(response.isSuccessful) { "MCP HTTP request failed: HTTP " + response.code }
            response.header("Mcp-Session-Id")?.let { sessionId = it }

            val bytes = response.body?.let(::readBoundedBody) ?: ByteArray(0)
            if (allowEmpty) {
                check(response.code == 202 && bytes.isEmpty()) {
                    "MCP notifications must be acknowledged with HTTP 202 and an empty body."
                }
                return JSONObject()
            }

            val expectedId = body.opt("id")
            val contentType = response.header("Content-Type").orEmpty()
            val parsed = when {
                contentType.contains("application/json", true) ->
                    JSONObject(String(bytes, Charsets.UTF_8))
                contentType.contains("text/event-stream", true) ->
                    parseSseResponse(String(bytes, Charsets.UTF_8), expectedId)
                else -> error("MCP server returned an unsupported Content-Type.")
            }
            check(parsed.optString("jsonrpc") == "2.0") { "MCP response is not a JSON-RPC 2.0 message." }
            check(parsed.has("id") && sameJsonRpcId(parsed.opt("id"), expectedId)) {
                "MCP response ID did not match the request."
            }
            parsed.optJSONObject("error")?.let { rpcError ->
                throw IllegalStateException(
                    "MCP error " + rpcError.optString("code") + ": " + rpcError.optString("message")
                )
            }
            return parsed
        }
    }

    private fun readBoundedBody(body: ResponseBody): ByteArray {
        val declaredLength = body.contentLength()
        check(declaredLength < 0L || declaredLength <= MAX_RESPONSE_BYTES.toLong()) {
            "MCP response exceeded the 1 MB safety limit."
        }

        val output = ByteArrayOutputStream(
            if (declaredLength in 1..MAX_RESPONSE_BYTES.toLong()) declaredLength.toInt() else 8192
        )
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val remainingWithOverflowByte = MAX_RESPONSE_BYTES + 1 - output.size()
                val count = input.read(buffer, 0, minOf(buffer.size, remainingWithOverflowByte))
                if (count < 0) break
                output.write(buffer, 0, count)
                check(output.size() <= MAX_RESPONSE_BYTES) {
                    "MCP response exceeded the 1 MB safety limit."
                }
            }
        }
        return output.toByteArray()
    }

    private fun parseSseResponse(raw: String, expectedId: Any?): JSONObject {
        val dataLines = mutableListOf<String>()
        val events = mutableListOf<String>()
        fun dispatchEvent() {
            if (dataLines.isNotEmpty()) {
                events += dataLines.joinToString("\n")
                dataLines.clear()
            }
        }

        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        normalized.split('\n').forEach { line ->
            if (line.isEmpty()) {
                dispatchEvent()
            } else if (!line.startsWith(":")) {
                val separator = line.indexOf(':')
                val field = if (separator < 0) line else line.substring(0, separator)
                if (field == "data") {
                    var value = if (separator < 0) "" else line.substring(separator + 1)
                    if (value.startsWith(" ")) value = value.substring(1)
                    dataLines += value
                }
            }
        }
        dispatchEvent()

        events.forEach { eventData ->
            if (eventData.isBlank()) return@forEach
            val message = JSONObject(eventData)
            check(message.optString("jsonrpc") == "2.0") {
                "MCP event stream contained a non-JSON-RPC message."
            }
            if (message.has("method") && message.has("id")) {
                error("MCP server-initiated requests are not supported by this client.")
            }
            if (message.has("id") && (message.has("result") || message.has("error"))) {
                check(sameJsonRpcId(message.opt("id"), expectedId)) {
                    "MCP event stream response ID did not match the request."
                }
                return message
            }
        }
        error("MCP event stream ended without a response matching the request ID.")
    }

    private fun sameJsonRpcId(actual: Any?, expected: Any?): Boolean {
        if (actual == null || expected == null ||
            actual === JSONObject.NULL || expected === JSONObject.NULL
        ) return false
        if (actual is Number && expected is Number) {
            val actualNumber = actual.toString().toBigDecimalOrNull() ?: return false
            val expectedNumber = expected.toString().toBigDecimalOrNull() ?: return false
            return actualNumber.compareTo(expectedNumber) == 0
        }
        return actual == expected
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val SUPPORTED_PROTOCOL_VERSION = "2025-11-25"
        private const val MAX_RESPONSE_BYTES = 1_048_576
        private const val MAX_TOOL_LIST_PAGES = 10
        private const val MAX_TOOLS = 50
    }
}
