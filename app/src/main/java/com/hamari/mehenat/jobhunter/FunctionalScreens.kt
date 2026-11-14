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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.hamari.mehenat.jobhunter.data.ApplicationPdfGenerator
import com.hamari.mehenat.jobhunter.data.DocumentTextExtractor
import com.hamari.mehenat.jobhunter.data.JobDiscoveryRepository
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
import com.hamari.mehenat.jobhunter.data.OfficialVisaResearchClient
import com.hamari.mehenat.jobhunter.data.VisaResearchResult
import com.hamari.mehenat.jobhunter.data.NzEmployerVerificationClient
import com.hamari.mehenat.jobhunter.data.NzEmployerLookupResult
import com.hamari.mehenat.jobhunter.data.NzEmployerLookupStatus
import com.hamari.mehenat.jobhunter.domain.EmploymentContractAnalyzer
import com.hamari.mehenat.jobhunter.domain.EmploymentContractReport
import com.hamari.mehenat.jobhunter.domain.ContractFindingSeverity
import com.hamari.mehenat.jobhunter.domain.JobListing
import com.hamari.mehenat.jobhunter.domain.JobMatcher
import com.hamari.mehenat.jobhunter.domain.JobProfile
import com.hamari.mehenat.jobhunter.domain.JobFreshnessService
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
    var countries by remember { mutableStateOf("New Zealand, Australia, United Kingdom") }
    var greenhouseBoards by remember { mutableStateOf("") }
    var leverSites by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<JobListing>>(emptyList()) }
    var status by remember { mutableStateOf("Search permitted public job feeds. Results include the original source link.") }
    var visaCountry by remember { mutableStateOf("Australia") }
    var visaResults by remember { mutableStateOf<List<VisaResearchResult>>(emptyList()) }
    var visaLoading by remember { mutableStateOf(false) }
    var visaStatus by remember { mutableStateOf("Official visa research fetches current public government pages and records retrieval time.") }
    var loading by remember { mutableStateOf(false) }
    var verifyingJobId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Job discovery", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("Public feeds only. No LinkedIn/Indeed login scraping or access-control bypass. Remotive listings are delayed 24 hours and its free API is capped at 4 calls/day; always verify the employer's live listing.", color = ScreenMuted, fontSize = 12.sp)
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
                            JobDiscoveryRepository(context).search(
                                query = query,
                                countries = countryList,
                                greenhouseBoards = greenhouseBoards.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                leverSites = leverSites.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            )
                        }
                        results = JobFreshnessService.removeExpired(sourceResults.flatMap { it.jobs })
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
        Text("ADDITIONAL JOB BOARDS", color = ScreenGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("These open the original job board in your browser. The app does not scrape login-protected pages.", color = ScreenMuted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {
                val url = "https://www.linkedin.com/jobs/search/?keywords=${Uri.encode(query)}&location=${Uri.encode(countries)}"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) { Text("LinkedIn") }
            OutlinedButton(onClick = {
                val url = "https://www.indeed.com/jobs?q=${Uri.encode(query)}&l=${Uri.encode(countries)}"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) { Text("Indeed") }
            OutlinedButton(onClick = {
                val url = "https://www.seek.com.au/jobs?keywords=${Uri.encode(query)}"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) { Text("SEEK") }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://eures.europa.eu/job-search_en")))
            }) { Text("EURES") }
        }
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
                        OutlinedButton(
                            enabled = verifyingJobId != job.id,
                            onClick = {
                                if (job.sourceName == "Himalayas") {
                                    val verificationUrl = job.verificationUrl
                                    if (verificationUrl.isNullOrBlank()) {
                                        status = "This Himalayas listing has no safe verification URL; open its source page first."
                                    } else {
                                        verifyingJobId = job.id
                                        status = "Checking whether this Himalayas vacancy is still open…"
                                        scope.launch {
                                            try {
                                                val isOpen = withContext(Dispatchers.IO) {
                                                    JobSourceClient().isHimalayasJobOpen(verificationUrl)
                                                }
                                                if (isOpen) {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(job.sourceUrl)))
                                                    status = "Vacancy verified open. The original applicationLink was opened."
                                                } else {
                                                    results = results.filterNot { it.id == job.id }
                                                    status = "This Himalayas vacancy has closed and was removed from the current results."
                                                }
                                            } catch (e: Exception) {
                                                status = "Could not verify vacancy status: ${e.message ?: e.javaClass.simpleName}. Open the Himalayas source to check manually."
                                            } finally {
                                                verifyingJobId = null
                                            }
                                        }
                                    }
                                } else {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(job.sourceUrl)))
                                    } catch (_: Exception) { status = "Could not open vacancy link." }
                                }
                            }
                        ) { Text(if (job.sourceName == "Himalayas") "Verify & apply" else "Open source") }
                        if (!job.sourcePageUrl.isNullOrBlank()) {
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(job.sourcePageUrl)))
                            }) { Text("Himalayas source") }
                        }
                        Button(onClick = {
                            val saved = ApplicationTrackerStore(context).save(job)
                            status = if (saved) "Vacancy saved to your local tracker." else "This vacancy is already saved."
                        }) { Text("Save job") }
                        OutlinedButton(onClick = {
                            val query = Uri.encode("${job.company} official website careers contact")
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")))
                        }) { Text("Research employer") }
                    }
                }
            }
        }
        Divider(color = Color(0xFF303945))
        Text("OFFICIAL VISA RESEARCH", color = ScreenGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        OutlinedTextField(value = visaCountry, onValueChange = { visaCountry = it }, label = { Text("Country") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(
            enabled = !visaLoading,
            onClick = {
                if (visaCountry.isBlank()) {
                    visaStatus = "Enter a country name."
                } else {
                    visaLoading = true
                    visaStatus = "Fetching official government pages…"
                    scope.launch {
                        val selected = OfficialVisaSources.sources.filter { it.country.equals(visaCountry.trim(), true) }
                        val fetched = withContext(Dispatchers.IO) {
                            selected.map { OfficialVisaResearchClient().fetch(it) }
                        }
                        visaResults = fetched
                        val evidence = fetched.filter { it.error == null && it.text.isNotBlank() }.joinToString("\\n\\n") {
                            "SOURCE: ${it.source.country} — ${it.source.title}\\nURL: ${it.source.url}\\nRetrieved: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.retrievedAtEpochMs))}\\nEXCERPT: ${it.text}"
                        }
                        if (evidence.isNotBlank()) {
                            val profile = LocalProfileStore(context).load() ?: org.json.JSONObject()
                            profile.put("official_visa_evidence", evidence.take(40000))
                            LocalProfileStore(context).save(profile.toString())
                        }
                        visaStatus = if (fetched.isEmpty()) {
                            "No official sources are registered for this country yet."
                        } else {
                            "Fetched ${fetched.count { it.error == null }} of ${fetched.size} registered official pages. Evidence is stored locally."
                        }
                        visaLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (visaLoading) "Fetching…" else "Fetch current official guidance") }
        Text(visaStatus, color = ScreenMuted, fontSize = 11.sp)
        visaResults.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ScreenPanel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.source.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        if (item.error == null) "HTTP ${item.statusCode} • Retrieved ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.retrievedAtEpochMs))}"
                        else "Could not fetch: ${item.error}",
                        color = if (item.error == null) ScreenGold else ScreenMuted,
                        fontSize = 10.sp
                    )
                    if (item.text.isNotBlank()) Text(item.text.take(1000), color = ScreenMuted, fontSize = 11.sp)
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.source.url)))
                    }) { Text("Open source") }
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
        Divider(color = Color(0xFF303945))
        NzVerificationPanel(context)
    }
}

@Composable
private fun NzVerificationPanel(context: Context) {
    var employerQuery by remember { mutableStateOf("") }
    var employerResult by remember { mutableStateOf<NzEmployerLookupResult?>(null) }
    var employerLoading by remember { mutableStateOf(false) }
    var contractText by remember { mutableStateOf("") }
    var expectedEmployer by remember { mutableStateOf("") }
    var expectedRole by remember { mutableStateOf("") }
    var expectedLocation by remember { mutableStateOf("") }
    var advertisedHourlyPay by remember { mutableStateOf("") }
    var contractReport by remember { mutableStateOf<EmploymentContractReport?>(null) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("NEW ZEALAND — EMPLOYER & CONTRACT CHECK", color = ScreenGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(
            "Search Immigration New Zealand's public accredited-employer register. An employer accreditation match does not prove that a specific vacancy has an approved job check.",
            color = ScreenMuted, fontSize = 11.sp
        )
        OutlinedTextField(
            value = employerQuery,
            onValueChange = { employerQuery = it; employerResult = null },
            label = { Text("Employer legal name or 13-digit NZBN") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = {
                employerLoading = true
                employerResult = null
                scope.launch {
                    employerResult = withContext(Dispatchers.IO) {
                        NzEmployerVerificationClient().lookup(employerQuery)
                    }
                    employerLoading = false
                }
            },
            enabled = !employerLoading && employerQuery.trim().length >= 3,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (employerLoading) "Checking INZ register…" else "Check employer accreditation") }

        employerResult?.let { result ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ScreenPanel)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        when (result.status) {
                            NzEmployerLookupStatus.ACCREDITED_NZBN_MATCH -> "Exact NZBN match — accreditation listed"
                            NzEmployerLookupStatus.EXPIRED_NZBN_MATCH -> "NZBN match — published accreditation expired"
                            NzEmployerLookupStatus.EXACT_NZBN_MATCH_EXPIRY_UNKNOWN -> "NZBN match — expiry date unavailable"
                            NzEmployerLookupStatus.EXACT_NAME_MATCH_REQUIRES_NZBN_CONFIRMATION -> "Employer name result — confirm NZBN"
                            NzEmployerLookupStatus.NO_PUBLIC_MATCH_INCONCLUSIVE -> "No public match — inconclusive"
                            NzEmployerLookupStatus.LOOKUP_FAILED -> "Lookup unavailable"
                        },
                        color = if (result.status == NzEmployerLookupStatus.ACCREDITED_NZBN_MATCH) ScreenGold else ScreenMuted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(result.message, color = ScreenMuted, fontSize = 11.sp)
                    result.employers.forEach { employer ->
                        Text(employer.legalName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        employer.tradingName?.let { Text("Trading name: $it", color = ScreenMuted, fontSize = 11.sp) }
                        Text("NZBN: ${employer.nzbn ?: "Not returned"}", color = ScreenMuted, fontSize = 11.sp)
                        Text("Accreditation expiry: ${employer.accreditationExpiryDate ?: "Not returned"}", color = ScreenMuted, fontSize = 11.sp)
                    }
                    Text("Checked: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(result.checkedAtEpochMs))}", color = ScreenMuted, fontSize = 10.sp)
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.sourceUrl)))
                    }) { Text("Open official INZ employer register") }
                    result.error?.let { Text("Technical detail: $it", color = ScreenMuted, fontSize = 10.sp) }
                }
            }
        }

        Text("SPECIFIC JOB CHECK", color = ScreenGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(
            "INZ does not provide a public worker-facing lookup for a job-check approval by vacancy. This app will not invent a verified result. Ask the employer for the job-check approval details and compare the role, location, pay and conditions with the signed offer.",
            color = ScreenMuted, fontSize = 11.sp
        )
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.immigration.govt.nz/work/for-employers/getting-accreditation-or-approval-to-hire/employer-accreditation-for-the-aewv/aewv-employer-accreditation-and-job-check-process/")))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open INZ job-check process") }

        Divider(color = Color(0xFF303945))
        Text("EMPLOYMENT AGREEMENT TEXT REVIEW", color = ScreenGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("Paste readable contract text below. This first-pass checklist flags missing terms and risk wording; it is not legal advice.", color = ScreenMuted, fontSize = 11.sp)
        OutlinedTextField(
            value = expectedEmployer,
            onValueChange = { expectedEmployer = it; contractReport = null },
            label = { Text("Expected employer legal name (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = expectedRole,
            onValueChange = { expectedRole = it; contractReport = null },
            label = { Text("Expected job title (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = expectedLocation,
            onValueChange = { expectedLocation = it; contractReport = null },
            label = { Text("Expected work location (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = advertisedHourlyPay,
            onValueChange = { advertisedHourlyPay = it; contractReport = null },
            label = { Text("Advertised hourly pay in NZD (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = contractText,
            onValueChange = { contractText = it; contractReport = null },
            label = { Text("Paste employment agreement text") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5
        )
        Button(
            onClick = {
                contractReport = EmploymentContractAnalyzer().analyze(
                    contractText = contractText,
                    expectedEmployerName = expectedEmployer.takeIf(String::isNotBlank),
                    expectedJobTitle = expectedRole.takeIf(String::isNotBlank),
                    expectedWorkLocation = expectedLocation.takeIf(String::isNotBlank),
                    advertisedHourlyPayNzd = advertisedHourlyPay.toDoubleOrNull()
                )
            },
            enabled = contractText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Analyze agreement") }

        contractReport?.let { report ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ScreenPanel)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(report.summary, color = if (report.hasHighRiskFlags) Color(0xFFE7A08B) else ScreenGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        "Detected hours: ${report.detectedWeeklyHours?.let { "$it/week" } ?: "Not detected"} • Hourly pay: ${report.detectedHourlyPayNzd?.let { "NZD $it" } ?: "Not detected"}",
                        color = ScreenMuted, fontSize = 11.sp
                    )
                    report.findings.forEach { finding ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${if (finding.severity == ContractFindingSeverity.HIGH_RISK) "⚠ " else "• "}${finding.title}",
                                color = if (finding.severity == ContractFindingSeverity.HIGH_RISK) Color(0xFFE7A08B) else ScreenGold,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                            Text(finding.detail, color = ScreenMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        Text("NEW ZEALAND JOB PORTALS", color = ScreenGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("These links open the source websites. Search results from permitted public feeds are separate; browser hand-off is used where no authorized job-search API is available.", color = ScreenMuted, fontSize = 11.sp)
        listOf(
            "SEEK New Zealand" to "https://www.seek.co.nz/",
            "Tradestaff" to "https://www.tradestaff.co.nz/",
            "Trade Assist" to "https://tradeassist.net/",
            "Enterprise Recruitment" to "https://www.enterprise.co.nz/"
        ).forEach { (label, url) ->
            OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open $label") }
        }
    }
}

@Composable
fun FilesScreen(context: Context) {
    var message by remember { mutableStateOf("Documents are encrypted with an Android Keystore AES-GCM key and kept in app-private storage.") }
    var documentIds by remember { mutableStateOf(EncryptedDocumentVault(context).listIds()) }
    var cvPreview by remember { mutableStateOf("") }
    var extractedText by remember { mutableStateOf("") }
    var extractedFileName by remember { mutableStateOf("") }
    var extractedContractReport by remember { mutableStateOf<EmploymentContractReport?>(null) }
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
                                    val (stored, extracted) = withContext(Dispatchers.IO) {
                                        val stored = EncryptedDocumentVault(context).readDocument(id)
                                        val extracted = DocumentTextExtractor(context).extract(stored.document.originalName, stored.bytes)
                                        stored to extracted
                                    }
                                    val profile = LocalProfileStore(context).load() ?: org.json.JSONObject()
                                    val isCv = stored.document.originalName.contains(Regex("(?i)(cv|resume)"))
                                    profile.put(if (isCv) "cv_text" else "last_document_text", extracted.text)
                                    LocalProfileStore(context).save(profile.toString())
                                    extractedText = extracted.text
                                    extractedFileName = stored.document.originalName
                                    extractedContractReport = null
                                    cvPreview = extracted.text.take(2200)
                                    message = "Extracted ${extracted.text.length} characters from ${extracted.format} and saved the extracted text encrypted in your profile."
                                } catch (e: Exception) {
                                    message = "Text extraction failed: ${e.message ?: e.javaClass.simpleName}"
                                }
                            }
                        }) { Text("Extract text") }
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
            if (extractedText.isNotBlank()) {
                Text("Extracted from: $extractedFileName", color = ScreenMuted, fontSize = 10.sp)
                Button(
                    onClick = {
                        extractedContractReport = EmploymentContractAnalyzer().analyze(extractedText)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Review extracted text as an employment agreement") }
                extractedContractReport?.let { report ->
                    Card(colors = CardDefaults.cardColors(containerColor = ScreenPanel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(report.summary, color = if (report.hasHighRiskFlags) Color(0xFFE7A08B) else ScreenGold, fontWeight = FontWeight.SemiBold)
                            report.findings.forEach { finding ->
                                Text(
                                    "${finding.title}: ${finding.detail}",
                                    color = if (finding.severity == ContractFindingSeverity.HIGH_RISK) Color(0xFFE7A08B) else ScreenMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
                    var notes by remember(item.id) { mutableStateOf(item.notes) }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Employer contact / response notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    TextButton(onClick = {
                        ApplicationTrackerStore(context).updateNotes(item.id, notes)
                        entries = ApplicationTrackerStore(context).all()
                    }) { Text("Save notes") }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("Saved", "Contacted", "Awaiting response", "Applied", "Interview", "Offer", "Rejected").forEach { stage ->
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
    var pdfDraftToExport by remember { mutableStateOf("") }
    var pdfExportTitle by remember { mutableStateOf("Application draft") }
    var pdfExportStatus by remember { mutableStateOf("") }
    var selectedAgent by remember { mutableStateOf(PersonalAgent.CV_ANALYST) }
    var agentMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val manager = remember(context) { LocalModelManager(context) }
    val pdfExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) {
            pdfExportStatus = "PDF export cancelled."
        } else {
            val draft = pdfDraftToExport
            val title = pdfExportTitle
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ApplicationPdfGenerator.write(context, uri, title, draft)
                    }
                    pdfExportStatus = "PDF saved to the destination you selected. Review it before sending."
                } catch (e: Exception) {
                    pdfExportStatus = "PDF export failed: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }
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
                                val officialVisaEvidence = profile?.optString("official_visa_evidence").orEmpty()
                                val selectedEvidence = if (selectedAgent == PersonalAgent.VISA_EVIDENCE) {
                                    listOf(officialVisaEvidence, agentEvidence)
                                } else {
                                    listOf(cvText, agentEvidence)
                                }
                                val evidence = selectedEvidence.filter { it.isNotBlank() }
                                    .joinToString("\\n\\n")
                                    .take(5000)
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
                    OutlinedButton(
                        onClick = {
                            pdfDraftToExport = agentOutput
                            pdfExportTitle = when (selectedAgent) {
                                PersonalAgent.CV_TAILOR -> "Tailored CV"
                                PersonalAgent.COVER_LETTER -> "Cover Letter"
                                else -> "Application Draft"
                            }
                            val safeName = pdfExportTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                            pdfExporter.launch("${safeName.ifBlank { "application_draft" }}.pdf")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export reviewed draft as PDF") }
                    if (pdfExportStatus.isNotBlank()) {
                        Text(pdfExportStatus, color = ScreenMuted, fontSize = 11.sp)
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
