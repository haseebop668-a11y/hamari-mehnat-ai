package com.hamari.mehenat.jobhunter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamari.mehenat.jobhunter.data.EncryptedDocumentVault
import com.hamari.mehenat.jobhunter.data.JobSourceClient
import com.hamari.mehenat.jobhunter.data.LocalProfileStore
import com.hamari.mehenat.jobhunter.data.ApplicationTrackerStore
import com.hamari.mehenat.jobhunter.domain.JobListing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ScreenGold = Color(0xFFD6B36A)
private val ScreenMuted = Color(0xFF9AA4B2)
private val ScreenPanel = Color(0xFF191F27)

@Composable
fun JobsScreen(context: Context) {
    var query by remember { mutableStateOf("mechanic") }
    var countries by remember { mutableStateOf("Australia, United Kingdom, Germany") }
    var results by remember { mutableStateOf<List<JobListing>>(emptyList()) }
    var status by remember { mutableStateOf("Search permitted public job feeds. Results include the original source link.") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Job discovery", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("Public feeds only. No LinkedIn/Indeed login scraping or access-control bypass.", color = ScreenMuted, fontSize = 12.sp)
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Role / keywords") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = countries, onValueChange = { countries = it }, label = { Text("Target countries (comma separated)") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                if (query.isBlank() || countries.isBlank()) {
                    status = "Enter a job keyword and at least one country."
                } else {
                    loading = true
                    status = "Connecting to public job APIs…"
                    scope.launch {
                        val countryList = countries.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val sourceResults = withContext(Dispatchers.IO) {
                            val api = JobSourceClient()
                            listOf(
                                api.searchArbeitnow(query, countryList),
                                api.searchRemotive(query, countryList)
                            )
                        }
                        results = sourceResults.flatMap { it.jobs }.distinctBy { it.id }
                        val failures = sourceResults.filter { it.error != null }.joinToString { "${it.source}: ${it.error}" }
                        status = if (failures.isBlank()) {
                            "Search completed. ${results.size} vacancies retrieved from ${sourceResults.size} public sources."
                        } else {
                            "Partial results. Source errors: $failures"
                        }
                        loading = false
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Searching…" else "Search jobs") }
        Text(status, color = ScreenMuted, fontSize = 12.sp)
        results.forEach { job ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ScreenPanel),
                border = BorderStroke(1.dp, Color(0xFF303945))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(job.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(job.company, color = ScreenGold, fontSize = 13.sp)
                    Text("${job.location} • ${job.sourceName}", color = ScreenMuted, fontSize = 11.sp)
                    Text(job.description.take(380), color = ScreenMuted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(job.sourceUrl)))
                            } catch (_: Exception) { status = "Could not open vacancy link." }
                        }) { Text("Open source") }
                        Button(onClick = {
                            val saved = ApplicationTrackerStore(context).save(job)
                            status = if (saved) "Vacancy saved to your local tracker." else "This vacancy is already saved."
                        }) { Text("Save job") }
                    }
                }
            }
        }
    }
}

@Composable
fun FilesScreen(context: Context) {
    var message by remember { mutableStateOf("Documents are encrypted with an Android Keystore AES-GCM key and kept in app-private storage.") }
    var documentIds by remember { mutableStateOf(EncryptedDocumentVault(context).listIds()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "Imported document"
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val document = EncryptedDocumentVault(context).import(uri, name, mime)
                documentIds = EncryptedDocumentVault(context).listIds()
                message = "Encrypted and imported: ${document.originalName} (${document.sizeBytes} bytes)."
            } catch (e: Exception) {
                message = "Import failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Private document vault", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("CVs, certificates, licenses and identity documents stay on this phone. Files are encrypted before writing to app storage.", color = ScreenMuted, fontSize = 12.sp)
        Button(onClick = {
            picker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "image/*"))
        }, modifier = Modifier.fillMaxWidth()) { Text("Import document securely") }
        Text(message, color = ScreenGold, fontSize = 12.sp)
        Text("Encrypted files: ${documentIds.size}", fontWeight = FontWeight.SemiBold)
        documentIds.forEach { id ->
            Card(colors = CardDefaults.cardColors(containerColor = ScreenPanel), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Private document • ${id.take(8)}…", fontSize = 12.sp)
                    TextButton(onClick = {
                        val deleted = EncryptedDocumentVault(context).delete(id)
                        documentIds = EncryptedDocumentVault(context).listIds()
                        message = if (deleted) "Encrypted document deleted." else "Document was not found."
                    }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
fun TrackerScreen(context: Context) {
    var entries by remember { mutableStateOf(ApplicationTrackerStore(context).all()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Application tracker", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("Saved jobs are stored locally. Change each stage as you progress.", color = ScreenMuted, fontSize = 12.sp)
        if (entries.isEmpty()) Text("No saved vacancies yet. Save a job from the Jobs tab.", color = ScreenMuted)
        entries.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ScreenPanel)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text(item.company, color = ScreenGold, fontSize = 12.sp)
                    Text(item.stage, color = ScreenMuted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Saved", "Applied", "Interview", "Offer", "Rejected").forEach { stage ->
                            TextButton(onClick = {
                                ApplicationTrackerStore(context).setStage(item.id, stage)
                                entries = ApplicationTrackerStore(context).all()
                            }) { Text(stage, fontSize = 9.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalAiScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("On-device AI", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("Local model runtime is the required V1 path. No cloud AI provider will receive your CV.", color = ScreenMuted, fontSize = 12.sp)
        Card(colors = CardDefaults.cardColors(containerColor = ScreenPanel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Model installation not yet enabled", color = ScreenGold, fontWeight = FontWeight.SemiBold)
                Text("Candidate checkpoints are being evaluated for model-card provenance, redistribution license, Android ARM64 compatibility, memory use and Urdu/Roman Urdu quality. The app will not show a model as ready until checksum verification and an on-device inference test pass.", color = ScreenMuted, fontSize = 12.sp)
            }
        }
        Text("Profile storage: ${if (LocalProfileStore(LocalContext.current).load() == null) "not configured" else "encrypted profile available"}", color = ScreenMuted, fontSize = 12.sp)
    }
}
