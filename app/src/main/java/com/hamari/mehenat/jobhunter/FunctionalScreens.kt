package com.hamari.mehenat.jobhunter

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.Manifest
import android.os.Build
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
import com.hamari.mehenat.jobhunter.data.DocumentTextExtractor
import com.hamari.mehenat.jobhunter.data.JobSourceClient
import com.hamari.mehenat.jobhunter.data.LocalProfileStore
import com.hamari.mehenat.jobhunter.data.LocalModelManager
import com.hamari.mehenat.jobhunter.ai.NativeLlama
import com.hamari.mehenat.jobhunter.ai.LocalAgentOrchestrator
import com.hamari.mehenat.jobhunter.ai.PersonalAgent
import com.hamari.mehenat.jobhunter.data.ApplicationTrackerStore
import com.hamari.mehenat.jobhunter.data.InterviewStore
import com.hamari.mehenat.jobhunter.data.InterviewRecord
import com.hamari.mehenat.jobhunter.data.InterviewReminderScheduler
import com.hamari.mehenat.jobhunter.data.OfficialVisaSources
import com.hamari.mehenat.jobhunter.domain.JobListing
import com.hamari.mehenat.jobhunter.domain.JobMatcher
import com.hamari.mehenat.jobhunter.domain.JobProfile
import com.hamari.mehenat.jobhunter.domain.SponsorshipSignal
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val ScreenGold = Color(0xFFD6B36A)
private val ScreenMuted = Color(0xFF9AA4B2)
private val ScreenPanel = Color(0xFF191F27)

@Composable
fun JobsScreen(context: Context) {
    var query by remember { mutableStateOf("mechanic") }
    var countries by remember { mutableStateOf("Australia, United Kingdom, Germany") }
    var greenhouseBoards by remember { mutableStateOf("") }
    var leverSites by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<JobListing>>(emptyList()) }
    var status by remember { mutableStateOf("Search permitted public job feeds. Results include the original source link.") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Job discovery", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("Public feeds only. No LinkedIn/Indeed login scraping or access-control bypass.", color = ScreenMuted, fontSize = 12.sp)
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Role / keywords") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = countries, onValueChange = { countries = it }, label = { Text("Target countries (comma separated)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = greenhouseBoards, onValueChange = { greenhouseBoards = it }, label = { Text("Optional Greenhouse company board IDs") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = leverSites, onValueChange = { leverSites = it }, label = { Text("Optional Lever company site names") }, modifier = Modifier.fillMaxWidth())
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
                                api.searchRemotive(query, countryList),
                                api.searchGreenhouse(query, countryList, greenhouseBoards.split(",").map { it.trim() }.filter { it.isNotBlank() }),
                                api.searchLever(query, countryList, leverSites.split(",").map { it.trim() }.filter { it.isNotBlank() })
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
        val savedProfile = remember(results) { LocalProfileStore(context).load() }
        val targetRoles = savedProfile?.optJSONArray("target_roles").asStringList()
        val targetCountries = savedProfile?.optJSONArray("target_countries").asStringList()
        val skills = savedProfile?.optJSONArray("skills").asStringList()
        results.forEach { job ->
            val match = JobMatcher().evaluate(
                job,
                JobProfile(targetRoles = targetRoles, targetCountries = targetCountries, skills = skills)
            )
            val sponsorshipLabel = when (match.sponsorship) {
                SponsorshipSignal.POSSIBLE_SIGNAL_REQUIRES_VERIFICATION -> "Possible sponsorship signal — verify"
                SponsorshipSignal.EXPLICITLY_UNAVAILABLE_OR_RIGHT_TO_WORK_REQUIRED -> "May require existing work rights"
                SponsorshipSignal.NOT_MENTIONED -> "Sponsorship not mentioned"
            }
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
                    Text("Relevance: ${match.score}/100 • $sponsorshipLabel", color = ScreenGold, fontSize = 11.sp)
                    if (match.reasons.isNotEmpty()) Text(match.reasons.joinToString(" • "), color = ScreenMuted, fontSize = 10.sp)
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
        Divider(color = Color(0xFF303945))
        Text("OFFICIAL VISA & SPONSORSHIP SOURCES", color = ScreenGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("Government sources only • links last checked 5 Nov 2026. Always open the current source; this app does not decide visa eligibility.", color = ScreenMuted, fontSize = 11.sp)
        OfficialVisaSources.sources.forEach { source ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ScreenPanel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${source.country} — ${source.title}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(source.purpose, color = ScreenMuted, fontSize = 11.sp)
                    TextButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                        } catch (_: Exception) { status = "Could not open official government source." }
                    }) { Text("Open official source") }
                }
            }
        }
    }
}

@Composable
fun FilesScreen(context: Context) {
    var message by remember { mutableStateOf("Documents are encrypted with an Android Keystore AES-GCM key and kept in app-private storage.") }
    var documentIds by remember { mutableStateOf(EncryptedDocumentVault(context).listIds()) }
    var cvPreview by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                message = "Importing and encrypting document…"
                try {
                    val document = withContext(Dispatchers.IO) {
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: "Imported document"
                        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        EncryptedDocumentVault(context).importDocument(uri, name, mime)
                    }
                    documentIds = EncryptedDocumentVault(context).listIds()
                    message = "Encrypted and imported: ${document.originalName} (${document.sizeBytes} bytes)."
                } catch (e: Exception) {
                    message = "Import failed: ${e.message ?: e.javaClass.simpleName}"
                }
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
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Private document • ${id.take(8)}…", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    val extracted = withContext(Dispatchers.IO) {
                                        val stored = EncryptedDocumentVault(context).readDocument(id)
                                        DocumentTextExtractor(context).extract(stored.document.originalName, stored.bytes)
                                    }
                                    val profile = LocalProfileStore(context).load() ?: org.json.JSONObject()
                                    profile.put("cv_text", extracted.text)
                                    LocalProfileStore(context).save(profile.toString())
                                    cvPreview = extracted.text.take(2200)
                                    message = "Extracted ${extracted.text.length} characters from ${extracted.format} and saved the CV text encrypted in your profile."
                                } catch (e: Exception) {
                                    message = "Text extraction failed: ${e.message ?: e.javaClass.simpleName}"
                                }
                            }
                        }) { Text("Extract CV text") }
                        TextButton(onClick = {
                            val deleted = EncryptedDocumentVault(context).delete(id)
                            documentIds = EncryptedDocumentVault(context).listIds()
                            message = if (deleted) "Encrypted document deleted." else "Document was not found."
                        }) { Text("Delete") }
                    }
                }
            }
        }
        if (cvPreview.isNotBlank()) {
            Text("CV text preview", color = ScreenGold, fontWeight = FontWeight.SemiBold)
            Text(cvPreview, color = ScreenMuted, fontSize = 11.sp)
        }
    }
}

@Composable
fun TrackerScreen(context: Context) {
    var entries by remember { mutableStateOf(ApplicationTrackerStore(context).all()) }
    var interviews by remember { mutableStateOf(InterviewStore(context).all()) }
    var company by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var dateTime by remember {
        mutableStateOf(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")))
    }
    var timezone by remember { mutableStateOf(ZoneId.systemDefault().id) }
    var location by remember { mutableStateOf("") }
    var interviewMessage by remember { mutableStateOf("Interview details are stored locally; a reminder is scheduled one hour before the interview.") }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) interviewMessage = "Interview saved, but notifications are disabled. Enable notification permission in Android settings."
    }

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
        Divider(color = Color(0xFF303945))
        Text("INTERVIEW PLANNER", color = ScreenGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Company") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Role") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = dateTime, onValueChange = { dateTime = it }, label = { Text("Local date/time (yyyy-MM-ddTHH:mm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = timezone, onValueChange = { timezone = it }, label = { Text("Time zone (e.g. Asia/Karachi)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Interview location or meeting link") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Button(onClick = {
            try {
                require(company.isNotBlank() && role.isNotBlank()) { "Company and role are required." }
                val epoch = LocalDateTime.parse(dateTime).atZone(ZoneId.of(timezone)).toInstant().toEpochMilli()
                val interview = InterviewRecord(
                    id = UUID.randomUUID().toString(),
                    applicationId = "",
                    company = company.trim(),
                    role = role.trim(),
                    scheduledAtEpochMs = epoch,
                    timeZoneId = timezone.trim(),
                    locationOrLink = location.trim(),
                    notes = ""
                )
                InterviewReminderScheduler.schedule(context, interview)
                InterviewStore(context).save(interview)
                interviews = InterviewStore(context).all()
                interviewMessage = "Interview saved. A local reminder is scheduled for one hour before."
                if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                company = ""
                role = ""
                location = ""
            } catch (e: Exception) {
                interviewMessage = "Could not schedule interview: ${e.message ?: e.javaClass.simpleName}"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save interview and reminder") }
        Text(interviewMessage, color = ScreenMuted, fontSize = 12.sp)
        interviews.forEach { interview ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ScreenPanel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${interview.role} • ${interview.company}", fontWeight = FontWeight.SemiBold)
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(interview.scheduledAtEpochMs)) + " • " + interview.timeZoneId, color = ScreenMuted, fontSize = 11.sp)
                    if (interview.locationOrLink.isNotBlank()) Text(interview.locationOrLink, color = ScreenMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun LocalAiScreen(context: Context) {
    var status by remember { mutableStateOf("Model not downloaded. Internet is required only for the first model download.") }
    var response by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var agentTask by remember { mutableStateOf("Review my profile and suggest the strongest honest next step for my job search.") }
    var agentEvidence by remember { mutableStateOf("") }
    var employerEmail by remember { mutableStateOf("") }
    var agentOutput by remember { mutableStateOf("") }
    var selectedAgent by remember { mutableStateOf(PersonalAgent.CV_ANALYST) }
    var agentMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val manager = remember(context) { LocalModelManager(context) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("On-device AI", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("Local model runtime is the required V1 path. No cloud AI provider will receive your CV.", color = ScreenMuted, fontSize = 12.sp)
        Card(colors = CardDefaults.cardColors(containerColor = ScreenPanel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LFM2.5 1.2B — QAD Q4_0", color = ScreenGold, fontWeight = FontWeight.SemiBold)
                Text("Teacher-distilled quantization-aware checkpoint • about 696 MB • Hugging Face • free LFM Open License (terms apply). The model is downloaded to private app storage and SHA-256 verified before loading.", color = ScreenMuted, fontSize = 12.sp)
                Text("Urdu is not listed among the model's official language tags, so the app must pass Urdu/Roman Urdu tests before we rely on it.", color = ScreenMuted, fontSize = 12.sp)
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        status = "Preparing local model download and verification…"
                        scope.launch {
                            try {
                                val output = withContext(Dispatchers.IO) {
                                    if (!manager.verifyInstalled()) {
                                        manager.deleteModel()
                                        manager.downloadModel { percent -> progress = percent }
                                    }
                                    check(NativeLlama.loadModel(manager.modelFile.absolutePath, 4096, 4)) {
                                        "Native llama.cpp could not load this model on this device."
                                    }
                                    NativeLlama.generate(
                                        "Reply in simple Roman Urdu. In one short paragraph, explain how you can help organize a truthful job search from a CV.",
                                        120
                                    )
                                }
                                loaded = true
                                response = output
                                status = "Model downloaded, checksum verified and local inference test completed."
                            } catch (e: Exception) {
                                status = "Local AI setup failed: ${e.message ?: e.javaClass.simpleName}"
                            } catch (e: UnsatisfiedLinkError) {
                                status = "Native inference library could not load: ${e.message}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Preparing model…" else if (manager.isDownloaded()) "Verify and load local model" else "Download and test local model (696 MB)") }
                if (busy && progress > 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(status, color = ScreenMuted, fontSize = 12.sp)
                if (loaded) {
                    OutlinedButton(onClick = {
                        NativeLlama.unload()
                        loaded = false
                        status = "Model unloaded from memory. The verified file remains on this phone."
                    }) { Text("Unload model from RAM") }
                }
                if (response.isNotBlank()) {
                    Text("Local inference test", color = ScreenGold, fontWeight = FontWeight.SemiBold)
                    Text(response, color = ScreenMuted, fontSize = 13.sp)
                }
                Divider(color = Color(0xFF303945))
                Text("LOCAL SPECIALIST AGENTS", color = ScreenGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Box {
                    OutlinedButton(onClick = { agentMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedAgent.displayName)
                    }
                    DropdownMenu(expanded = agentMenuOpen, onDismissRequest = { agentMenuOpen = false }) {
                        PersonalAgent.values().forEach { agent ->
                            DropdownMenuItem(
                                text = { Text(agent.displayName) },
                                onClick = { selectedAgent = agent; agentMenuOpen = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = agentTask,
                    onValueChange = { agentTask = it },
                    label = { Text("What should this agent do?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = agentEvidence,
                    onValueChange = { agentEvidence = it },
                    label = { Text("Vacancy / official-source evidence (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                if (selectedAgent == PersonalAgent.EMPLOYER_OUTREACH) {
                    OutlinedTextField(
                        value = employerEmail,
                        onValueChange = { employerEmail = it },
                        label = { Text("Employer email (only if verified)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                Button(
                    enabled = loaded && !busy && agentTask.isNotBlank(),
                    onClick = {
                        busy = true
                        status = "Running ${selectedAgent.displayName} locally…"
                        scope.launch {
                            try {
                                val profile = LocalProfileStore(context).load()
                                val profileFacts = org.json.JSONObject().apply {
                                    listOf("full_name", "email", "phone", "target_roles", "target_countries", "skills", "experience_summary").forEach { key ->
                                        if (profile?.has(key) == true) put(key, profile.opt(key))
                                    }
                                }.toString()
                                val cvText = profile?.optString("cv_text").orEmpty()
                                val evidence = listOf(cvText, agentEvidence).filter { it.isNotBlank() }.joinToString("\\n\\n").take(5000)
                                val output = withContext(Dispatchers.IO) {
                                    LocalAgentOrchestrator().execute(
                                        selectedAgent,
                                        agentTask,
                                        profileFacts.take(3000),
                                        evidence,
                                        320
                                    )
                                }
                                agentOutput = output
                                status = "Agent completed on-device. Review every generated statement before use."
                            } catch (e: Exception) {
                                status = "Agent failed: ${e.message ?: e.javaClass.simpleName}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Working locally…" else "Run selected agent") }
                if (agentOutput.isNotBlank()) {
                    Text("Agent output", color = ScreenGold, fontWeight = FontWeight.SemiBold)
                    Text(agentOutput, color = ScreenMuted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Job Hunter AI draft", agentOutput))
                            status = "Draft copied. Review it before using."
                        }) { Text("Copy") }
                        OutlinedButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, agentOutput)
                                putExtra(Intent.EXTRA_SUBJECT, "Personal job-search draft")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share draft"))
                        }) { Text("Share") }
                        if (selectedAgent == PersonalAgent.EMPLOYER_OUTREACH) {
                            Button(onClick = {
                                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(employerEmail).matches()) {
                                    status = "Enter a verified employer email address before opening an email draft."
                                } else {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.fromParts("mailto", employerEmail.trim(), null)
                                        putExtra(Intent.EXTRA_SUBJECT, "Job enquiry")
                                        putExtra(Intent.EXTRA_TEXT, agentOutput)
                                    }
                                    context.startActivity(intent)
                                }
                            }) { Text("Open email draft") }
                        }
                    }
                }
                TextButton(onClick = {
                    if (loaded) NativeLlama.unload()
                    loaded = false
                    val deleted = manager.deleteModel()
                    status = if (deleted) "Model file deleted from app storage." else "Could not delete model file."
                    response = ""
                }, enabled = !busy) { Text("Delete downloaded model") }
            }
        }
        Text("Profile storage: ${if (LocalProfileStore(LocalContext.current).load() == null) "not configured" else "encrypted profile available"}", color = ScreenMuted, fontSize = 12.sp)
    }
}


private fun JSONArray?.asStringList(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).trim().takeIf(String::isNotBlank) }
