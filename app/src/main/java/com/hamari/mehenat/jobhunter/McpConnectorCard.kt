package com.hamari.mehenat.jobhunter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamari.mehenat.jobhunter.data.McpStreamableHttpClient
import com.hamari.mehenat.jobhunter.data.McpToolDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun McpConnectorCard() {
    val scope = rememberCoroutineScope()
    var endpoint by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var arguments by remember { mutableStateOf("{}") }
    var client by remember { mutableStateOf<McpStreamableHttpClient?>(null) }
    var serverName by remember { mutableStateOf("") }
    var tools by remember { mutableStateOf<List<McpToolDescriptor>>(emptyList()) }
    var selectedTool by remember { mutableStateOf<McpToolDescriptor?>(null) }
    var pendingTool by remember { mutableStateOf<McpToolDescriptor?>(null) }
    var result by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Connect a trusted MCP Streamable HTTP server. No remote code is downloaded or run.") }
    var busy by remember { mutableStateOf(false) }

    if (pendingTool != null) {
        AlertDialog(
            onDismissRequest = { pendingTool = null },
            title = { Text("Run the selected server tool?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val host = runCatching { java.net.URI(endpoint.trim()).host }.getOrNull()
                        ?: "custom HTTPS endpoint"
                    Text("Server host: $host", fontWeight = FontWeight.SemiBold)
                    Text("Tool: \${pendingTool?.name.orEmpty()}")
                    Text("Arguments to send:\n$arguments")
                    Text("Security warning: read-only labels come from the server and are not trustworthy security controls. Only run this tool if you trust the server and have checked the tool and every argument. This app does not attach your profile or documents automatically.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val tool = pendingTool ?: return@TextButton
                    pendingTool = null
                    busy = true
                    scope.launch {
                        try {
                            val parsed = JSONObject(arguments)
                            result = withContext(Dispatchers.IO) {
                                client?.callReadOnlyTool(tool.name, parsed) ?: error("MCP server disconnected.")
                            }
                            status = "Tool completed. Review its returned data before using it."
                        } catch (e: Exception) {
                            status = "Tool call failed: ${e.message ?: e.javaClass.simpleName}"
                        } finally { busy = false }
                    }
                }) { Text("Run selected tool") }
            },
            dismissButton = { TextButton(onClick = { pendingTool = null }) { Text("Cancel") } }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FieldbookPalette.Paper),
        border = BorderStroke(1.dp, FieldbookPalette.Rule)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("MCP CONNECTOR", color = FieldbookPalette.Rust, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Text("Connect a trusted tool server", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            Text("Limited MCP Streamable HTTP support. Use an HTTPS endpoint and optional bearer token. Server read-only annotations are untrusted hints; review every tool and argument before running it. OAuth is not enabled, and account passwords must never be pasted here.", color = FieldbookPalette.Muted, fontSize = 11.sp)
            if (client == null) {
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("MCP HTTPS endpoint") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(token, { token = it }, label = { Text("Bearer token (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                Button(
                    enabled = endpoint.startsWith("https://") && !busy,
                    onClick = {
                        busy = true
                        status = "Negotiating an MCP session and discovering tools advertised as read-only…"
                        scope.launch {
                            try {
                                val connection = withContext(Dispatchers.IO) {
                                    val newClient = McpStreamableHttpClient(endpoint.trim(), token.takeIf(String::isNotBlank))
                                    try {
                                        val name = newClient.connect()
                                        val available = newClient.listReadOnlyTools()
                                        Triple(newClient, name, available)
                                    } catch (error: Exception) {
                                        runCatching { newClient.disconnect() }
                                        throw error
                                    }
                                }
                                client = connection.first
                                serverName = connection.second
                                tools = connection.third
                                token = ""
                                status = "Connected to $serverName. ${tools.size} read-only tools are available."
                            } catch (e: Exception) {
                                status = "Connection failed: ${e.message ?: e.javaClass.simpleName}"
                            } finally { busy = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Connecting…" else "Connect and discover tools") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(serverName, color = FieldbookPalette.Forest, fontWeight = FontWeight.SemiBold)
                        Text("${tools.size} server-advertised read-only tools", color = FieldbookPalette.Muted, fontSize = 10.sp)
                    }
                    OutlinedButton(onClick = {
                        val active = client
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { active?.disconnect() }
                                status = "MCP session closed. The bearer token was held only in memory."
                            } catch (e: Exception) {
                                status = "Local disconnect completed; server session close reported: ${e.message ?: e.javaClass.simpleName}"
                            } finally {
                                client = null
                                tools = emptyList()
                                selectedTool = null
                                result = ""
                                busy = false
                            }
                        }
                    }, enabled = !busy) { Text("Disconnect") }
                }
                tools.forEach { tool ->
                    OutlinedButton(
                        onClick = { selectedTool = tool; result = "" },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(tool.name, fontWeight = FontWeight.SemiBold)
                            if (tool.description.isNotBlank()) Text(tool.description, fontSize = 10.sp, color = FieldbookPalette.Muted)
                        }
                    }
                }
                selectedTool?.let { tool ->
                    Text("Arguments for ${tool.name}", fontWeight = FontWeight.SemiBold, color = FieldbookPalette.Ink)
                    if (tool.description.isNotBlank()) Text(tool.description, color = FieldbookPalette.Muted, fontSize = 11.sp)
                    Text("Untrusted input schema: ${tool.inputSchema.toString().take(1000)}", color = FieldbookPalette.Muted, fontSize = 9.sp)
                    OutlinedTextField(arguments, { arguments = it.take(2048) }, label = { Text("JSON object (max 2 KB)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    Button(
                        onClick = {
                            if (runCatching { JSONObject(arguments) }.isSuccess && arguments.length <= 2048) pendingTool = tool
                            else status = "Enter a valid JSON object no larger than 2 KB."
                        },
                        enabled = !busy && arguments.length <= 2048 && runCatching { JSONObject(arguments) }.isSuccess,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Review and run selected tool") }
                }
                if (result.isNotBlank()) {
                    Text("TOOL RESULT", color = FieldbookPalette.Rust, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(result, color = FieldbookPalette.Ink, fontSize = 12.sp)
                }
            }
            if (busy) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(status, color = FieldbookPalette.Muted, fontSize = 11.sp)
        }
    }
}
