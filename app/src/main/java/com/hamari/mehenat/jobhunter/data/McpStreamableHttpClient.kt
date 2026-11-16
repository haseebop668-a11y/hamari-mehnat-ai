package com.hamari.mehenat.jobhunter.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class McpHeaderParameter(
    val headerName: String,
    val propertyPath: List<String>,
    val primitiveType: String
)

data class McpToolDescriptor(
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
    val readOnly: Boolean,
    val headerParameters: List<McpHeaderParameter> = emptyList()
)

private enum class McpProtocolMode { UNKNOWN, MODERN, LEGACY }

private class McpHttpException(
    val statusCode: Int,
    val rpcError: JSONObject?,
    detail: String
) : IllegalStateException(detail) {
    val rpcErrorCode: Int? get() = rpcError?.optInt("code")
    val rpcErrorData: JSONObject? get() = rpcError?.optJSONObject("data")
}

private class McpRpcException(
    val rpcErrorCode: Int,
    val rpcErrorData: JSONObject?,
    detail: String
) : IllegalStateException(detail)

/**
 * MCP Streamable HTTP client:
 * - Current 2026-07-28 stateless protocol with per-request metadata.
 * - Legacy 2025-11-25/2025-06-18/2025-03-26 initialize-handshake fallback.
 *
 * Tool annotations are untrusted hints, not an authorization boundary. Every
 * tool call must be reviewed and explicitly started by the user. This client
 * does not automatically attach a CV, profile or imported document.
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
    private var protocolVersion = CURRENT_PROTOCOL_VERSION
    private var protocolMode = McpProtocolMode.UNKNOWN
    private var initialized = false
    private var toolsSupported = true

    init {
        val parsed = java.net.URI(endpoint)
        require(parsed.scheme.equals("https", true)) { "MCP server must use HTTPS." }
        require(parsed.host != null && parsed.userInfo == null) {
            "Enter a valid HTTPS MCP endpoint without embedded credentials."
        }
        require(endpoint.length <= 2048) { "MCP endpoint is too long." }
        require(bearerToken == null || bearerToken.length <= 4096) {
            "Authorization token is too long."
        }
    }

    @Synchronized
    fun connect(): String {
        check(!initialized) { "This MCP connection is already connected." }
        protocolMode = McpProtocolMode.MODERN
        protocolVersion = CURRENT_PROTOCOL_VERSION

        val discovery: JSONObject = try {
            request("server/discover", JSONObject())
        } catch (error: McpHttpException) {
            return handleModernProbeFailure(error)
        } catch (error: McpRpcException) {
            return when (error.rpcErrorCode) {
                -32022 -> connectLegacyIfAdvertised(error.rpcErrorData?.optJSONArray("supported"))
                -32601, -32602 -> connectLegacy()
                else -> throw error
            }
        }

        val result = discovery.getJSONObject("result")
        check(result.optString("resultType") == "complete") {
            "MCP server discovery returned an unsupported result type."
        }
        val versions = result.optJSONArray("supportedVersions")?.toStringList().orEmpty()
        if (CURRENT_PROTOCOL_VERSION in versions) {
            protocolMode = McpProtocolMode.MODERN
            protocolVersion = CURRENT_PROTOCOL_VERSION
            initialized = true
            toolsSupported = result.optJSONObject("capabilities")?.has("tools") == true
            val serverInfo = result.optJSONObject("_meta")
                ?.optJSONObject("io.modelcontextprotocol/serverInfo")
            return serverInfo?.optString("name").orEmpty().ifBlank {
                "MCP server connected (current protocol)"
            }
        }
        if (versions.any { it in LEGACY_PROTOCOL_VERSIONS }) return connectLegacy()

        throw IllegalStateException(
            "MCP server supports no protocol version this app implements. Supported versions: " +
                versions.joinToString().ifBlank { "not supplied" }
        )
    }

    private fun handleModernProbeFailure(error: McpHttpException): String {
        when (error.rpcErrorCode) {
            -32020, -32021 -> throw error
            -32022 -> return connectLegacyIfAdvertised(error.rpcErrorData?.optJSONArray("supported"))
        }
        if (error.statusCode in setOf(400, 404, 405)) return connectLegacy()
        throw error
    }

    private fun connectLegacyIfAdvertised(supported: JSONArray?): String {
        val versions = supported?.toStringList().orEmpty()
        check(versions.any { it in LEGACY_PROTOCOL_VERSIONS }) {
            "MCP server rejected the current protocol and advertises no compatible legacy version."
        }
        return connectLegacy()
    }

    private fun connectLegacy(): String {
        protocolMode = McpProtocolMode.LEGACY
        protocolVersion = LEGACY_PREFERRED_VERSION
        val response = request(
            "initialize",
            JSONObject()
                .put("protocolVersion", LEGACY_PREFERRED_VERSION)
                .put("capabilities", JSONObject())
                .put(
                    "clientInfo",
                    JSONObject().put("name", "Hamari Mehnat").put("version", CLIENT_VERSION)
                )
        )
        val result = response.getJSONObject("result")
        val negotiated = result.optString("protocolVersion")
        check(negotiated in LEGACY_PROTOCOL_VERSIONS) {
            "MCP server selected unsupported legacy protocol version: " +
                negotiated.ifBlank { "missing" } + "."
        }
        protocolVersion = negotiated
        initialized = true
        notifyInitialized()
        toolsSupported = result.optJSONObject("capabilities")?.has("tools") != false
        return result.optJSONObject("serverInfo")
            ?.optString("name").orEmpty().ifBlank { "Legacy MCP server connected" }
    }

    @Synchronized
    fun disconnect() {
        try {
            val activeSession = sessionId
            if (protocolMode == McpProtocolMode.LEGACY && activeSession != null) {
                val builder = Request.Builder()
                    .url(endpoint)
                    .header("Mcp-Session-Id", activeSession)
                    .header("MCP-Protocol-Version", protocolVersion)
                    .delete()
                bearerToken?.takeIf(String::isNotBlank)?.let {
                    builder.header("Authorization", "Bearer " + it)
                }
                http.newCall(builder.build()).execute().use { response ->
                    check(response.code in setOf(200, 202, 204, 404, 405)) {
                        "MCP session close failed: HTTP " + response.code
                    }
                }
            }
        } finally {
            sessionId = null
            initialized = false
            toolsSupported = true
            protocolMode = McpProtocolMode.UNKNOWN
            protocolVersion = CURRENT_PROTOCOL_VERSION
        }
    }

    @Synchronized
    fun listReadOnlyTools(): List<McpToolDescriptor> {
        check(initialized) { "Connect to the MCP server first." }
        if (!toolsSupported) return emptyList()

        val discovered = linkedMapOf<String, McpToolDescriptor>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null

        for (page in 0 until MAX_TOOL_LIST_PAGES) {
            val params = JSONObject()
            cursor?.let { params.put("cursor", it) }
            val result = request("tools/list", params).getJSONObject("result")
            ensureCompleteResult(result, "tools/list")
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
                val inputSchema = item.optJSONObject("inputSchema")
                    ?: JSONObject().put("type", "object")
                val headerParameters = collectHeaderParameters(inputSchema) ?: continue
                discovered.putIfAbsent(
                    name,
                    McpToolDescriptor(
                        name = name,
                        description = item.optString("description").take(1000),
                        inputSchema = inputSchema,
                        readOnly = true,
                        headerParameters = headerParameters
                    )
                )
                if (discovered.size >= MAX_TOOLS) return discovered.values.toList()
            }

            val nextCursor = result.opt("nextCursor") as? String
            if (nextCursor.isNullOrBlank()) return discovered.values.toList()
            check(seenCursors.add(nextCursor)) {
                "MCP tools/list returned a repeated pagination cursor."
            }
            cursor = nextCursor
        }
        check(cursor == null) { "MCP tools/list exceeded the pagination safety limit." }
        return discovered.values.toList()
    }

    @Synchronized
    fun callReadOnlyTool(name: String, arguments: JSONObject): String {
        check(initialized) { "Connect to the MCP server first." }
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,128}"))) { "Invalid MCP tool name." }
        require(arguments.toString().length <= MAX_ARGUMENT_BYTES) {
            "MCP tool arguments are too large."
        }

        val tool = listReadOnlyTools().firstOrNull { it.name == name }
            ?: throw IllegalStateException("This tool is not in the approved read-only tool list.")
        val headers = buildParameterHeaders(tool, arguments)
        val result = request(
            "tools/call",
            JSONObject().put("name", name).put("arguments", arguments),
            headers
        ).getJSONObject("result")
        ensureCompleteResult(result, "tools/call")
        check(!result.optBoolean("isError", false)) {
            result.optJSONArray("content")?.let { array ->
                (0 until array.length())
                    .mapNotNull { array.optJSONObject(it)?.optString("text") }
                    .joinToString("\n")
            }?.take(500) ?: "MCP tool returned an error."
        }
        val content = result.optJSONArray("content") ?: JSONArray()
        val text = (0 until content.length()).mapNotNull { index ->
            content.optJSONObject(index)?.optString("text")?.takeIf(String::isNotBlank)
        }.joinToString("\n")
        return text.ifBlank { result.toString() }.take(MAX_TOOL_OUTPUT_CHARS)
    }

    private fun ensureCompleteResult(result: JSONObject, operation: String) {
        val resultType = result.optString("resultType", "complete")
        check(resultType == "complete") {
            if (resultType == "input_required") {
                "MCP " + operation + " requested additional input. This client does not support that flow; no additional data was sent."
            } else {
                "MCP " + operation + " returned unsupported result type: " + resultType + "."
            }
        }
        if (protocolMode == McpProtocolMode.MODERN) {
            check(result.has("resultType")) {
                "Current MCP responses must include resultType."
            }
        }
    }

    private fun notifyInitialized() {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", "notifications/initialized")
        execute(body, allowEmpty = true)
    }

    private fun request(
        method: String,
        params: JSONObject,
        additionalHeaders: Map<String, String> = emptyMap()
    ): JSONObject {
        check(protocolMode != McpProtocolMode.UNKNOWN) {
            "MCP protocol mode has not been selected."
        }
        val requestParams = JSONObject(params.toString())
        if (protocolMode == McpProtocolMode.MODERN) {
            requestParams.put("_meta", requestMetadata())
        }
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", ids.getAndIncrement())
            .put("method", method)
            .put("params", requestParams)
        return execute(body, allowEmpty = false, additionalHeaders = additionalHeaders)
    }

    private fun requestMetadata(): JSONObject = JSONObject()
        .put("io.modelcontextprotocol/protocolVersion", CURRENT_PROTOCOL_VERSION)
        .put(
            "io.modelcontextprotocol/clientInfo",
            JSONObject().put("name", "Hamari Mehnat").put("version", CLIENT_VERSION)
        )
        .put("io.modelcontextprotocol/clientCapabilities", JSONObject())

    private fun execute(
        body: JSONObject,
        allowEmpty: Boolean,
        additionalHeaders: Map<String, String> = emptyMap()
    ): JSONObject {
        val builder = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json, text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))

        if (protocolMode == McpProtocolMode.LEGACY) {
            sessionId?.let { builder.header("Mcp-Session-Id", it) }
            if (initialized) builder.header("MCP-Protocol-Version", protocolVersion)
        } else if (protocolMode == McpProtocolMode.MODERN) {
            val method = body.optString("method")
            builder.header("MCP-Protocol-Version", CURRENT_PROTOCOL_VERSION)
            builder.header("Mcp-Method", method)
            val params = body.optJSONObject("params")
            val name = when (method) {
                "tools/call" -> params?.optString("name")
                "resources/read" -> params?.optString("uri")
                "prompts/get" -> params?.optString("name")
                else -> null
            }
            if (!name.isNullOrBlank()) builder.header("Mcp-Name", encodeHeaderValue(name))
            additionalHeaders.forEach { (key, value) -> builder.header(key, value) }
        }

        bearerToken?.takeIf(String::isNotBlank)?.let {
            builder.header("Authorization", "Bearer " + it)
        }

        http.newCall(builder.build()).execute().use { response ->
            check(response.code !in REDIRECT_CODES) {
                "MCP endpoint redirected. Use the final HTTPS endpoint directly."
            }
            val bytes = response.body?.let(::readBoundedBody) ?: ByteArray(0)
            if (!response.isSuccessful) {
                val errorObject = runCatching {
                    JSONObject(String(bytes, Charsets.UTF_8)).optJSONObject("error")
                }.getOrNull()
                val detail = errorObject?.optString("message")
                    ?.take(MAX_ERROR_MESSAGE_CHARS)
                    .orEmpty()
                    .ifBlank { "MCP HTTP request failed: HTTP " + response.code }
                throw McpHttpException(response.code, errorObject, detail)
            }

            if (protocolMode == McpProtocolMode.LEGACY) {
                response.header("Mcp-Session-Id")?.let { sessionId = it }
            }
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
            check(parsed.optString("jsonrpc") == "2.0") {
                "MCP response is not a JSON-RPC 2.0 message."
            }
            check(parsed.has("id") && sameJsonRpcId(parsed.opt("id"), expectedId)) {
                "MCP response ID did not match the request."
            }
            parsed.optJSONObject("error")?.let { rpcError ->
                throw McpRpcException(
                    rpcError.optInt("code"),
                    rpcError.optJSONObject("data"),
                    "MCP error " + rpcError.optString("code") + ": " +
                        rpcError.optString("message").take(MAX_ERROR_MESSAGE_CHARS)
                )
            }
            check(parsed.has("result")) { "MCP JSON-RPC response did not include a result." }
            return parsed
        }
    }

    private fun buildParameterHeaders(
        tool: McpToolDescriptor,
        arguments: JSONObject
    ): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        tool.headerParameters.forEach { parameter ->
            val value = valueAtPath(arguments, parameter.propertyPath) ?: return@forEach
            if (value === JSONObject.NULL) return@forEach
            val stringValue = when (parameter.primitiveType) {
                "string" -> value as? String
                    ?: throw IllegalArgumentException(
                        "MCP argument '" + parameter.propertyPath.joinToString(".") + "' must be a string."
                    )
                "boolean" -> (value as? Boolean)?.toString()
                    ?: throw IllegalArgumentException(
                        "MCP argument '" + parameter.propertyPath.joinToString(".") + "' must be a boolean."
                    )
                "integer" -> {
                    val decimal = (value as? Number)?.toString()?.toBigDecimalOrNull()
                        ?: throw IllegalArgumentException(
                            "MCP argument '" + parameter.propertyPath.joinToString(".") + "' must be an integer."
                        )
                    val normalized = decimal.stripTrailingZeros()
                    val min = BigDecimal("-9007199254740991")
                    val max = BigDecimal("9007199254740991")
                    require(normalized.scale() <= 0 && normalized >= min && normalized <= max) {
                        "MCP integer header parameter is outside the safe integer range."
                    }
                    normalized.toBigIntegerExact().toString()
                }
                else -> throw IllegalStateException("Invalid MCP header parameter schema.")
            }
            headers["Mcp-Param-" + parameter.headerName] = encodeHeaderValue(stringValue)
        }
        return headers
    }

    private fun valueAtPath(arguments: JSONObject, path: List<String>): Any? {
        var current: Any? = arguments
        for (segment in path) {
            current = (current as? JSONObject)?.opt(segment) ?: return null
        }
        return current
    }

    private fun collectHeaderParameters(schema: JSONObject): List<McpHeaderParameter>? {
        val parameters = mutableListOf<McpHeaderParameter>()
        val usedNames = mutableSetOf<String>()
        var valid = true

        fun containsAnnotation(value: Any?): Boolean = when (value) {
            is JSONObject -> {
                if (value.has("x-mcp-header")) true
                else {
                    val keys = value.keys()
                    var found = false
                    while (keys.hasNext() && !found) {
                        found = containsAnnotation(value.opt(keys.next()))
                    }
                    found
                }
            }
            is JSONArray -> (0 until value.length()).any { containsAnnotation(value.opt(it)) }
            else -> false
        }

        fun visit(node: JSONObject, path: List<String>) {
            if (node.has("x-mcp-header")) {
                val headerName = node.opt("x-mcp-header") as? String
                val primitiveType = node.optString("type")
                if (
                    path.isEmpty() ||
                    headerName.isNullOrBlank() ||
                    !HEADER_TOKEN.matches(headerName) ||
                    !usedNames.add(headerName.lowercase(Locale.ROOT)) ||
                    primitiveType !in SUPPORTED_HEADER_TYPES
                ) {
                    valid = false
                } else {
                    parameters += McpHeaderParameter(headerName, path, primitiveType)
                }
            }

            val keys = node.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key != "properties" && key != "x-mcp-header" &&
                    containsAnnotation(node.opt(key))
                ) {
                    valid = false
                }
            }

            val properties = node.optJSONObject("properties") ?: return
            val propertyKeys = properties.keys()
            while (propertyKeys.hasNext()) {
                val propertyName = propertyKeys.next()
                val propertySchema = properties.optJSONObject(propertyName)
                if (propertySchema == null) {
                    if (containsAnnotation(properties.opt(propertyName))) valid = false
                } else {
                    visit(propertySchema, path + propertyName)
                }
            }
        }

        visit(schema, emptyList())
        return if (valid) parameters else null
    }

    private fun encodeHeaderValue(value: String): String {
        val safeVisibleAscii = value == value.trim() && value.all {
            it == '\t' || it in ' '..'~'
        }
        if (safeVisibleAscii) return value
        val encoded = java.util.Base64.getEncoder()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
        return "=?base64?" + encoded + "?="
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

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val HEADER_TOKEN = Regex("[!#$%&'*+.^_\\\\x60|~0-9A-Za-z-]+")
        private val SUPPORTED_HEADER_TYPES = setOf("string", "integer", "boolean")
        private val LEGACY_PROTOCOL_VERSIONS = listOf(
            "2025-11-25",
            "2025-06-18",
            "2025-03-26"
        )
        private const val CURRENT_PROTOCOL_VERSION = "2026-07-28"
        private const val LEGACY_PREFERRED_VERSION = "2025-11-25"
        private const val CLIENT_VERSION = "1.0.0"
        private const val MAX_RESPONSE_BYTES = 1_048_576
        private const val MAX_TOOL_LIST_PAGES = 10
        private const val MAX_TOOLS = 50
        private const val MAX_ARGUMENT_BYTES = 16_384
        private const val MAX_TOOL_OUTPUT_CHARS = 12_000
        private const val MAX_ERROR_MESSAGE_CHARS = 300
        private val REDIRECT_CODES = setOf(301, 302, 307, 308)
    }
}
