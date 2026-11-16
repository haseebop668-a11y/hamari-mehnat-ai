package com.hamari.mehenat.jobhunter

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamari.mehenat.jobhunter.ai.LocalAgentOrchestrator
import com.hamari.mehenat.jobhunter.ai.NativeLlama
import com.hamari.mehenat.jobhunter.ai.PersonalAgent
import com.hamari.mehenat.jobhunter.data.ApplicationPdfGenerator
import com.hamari.mehenat.jobhunter.data.LocalModelManager
import com.hamari.mehenat.jobhunter.data.LocalProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class CvQuestion(val key: String, val prompt: String, val helper: String)

private val CvCoreQuestions = listOf(
    CvQuestion("full_name", "What name should appear on your CV?", "Use your professional name."),
    CvQuestion("email", "What email address can employers use?", "Use an email you check regularly."),
    CvQuestion("phone", "What phone number should employers use?", "Include country code if applying overseas."),
    CvQuestion("education_summary", "Tell us about your education.", "School, qualification, subjects and dates. Be accurate about completed levels."),
    CvQuestion("work_history", "What work have you actually done?", "Employer, role, dates, duties and tools. Informal work is okay if described honestly."),
    CvQuestion("skills", "Which practical skills can you demonstrate?", "List tools, machinery, software, languages and work skills."),
    CvQuestion("certifications", "Which licences or certificates have you completed?", "Include issuing body and expiry if known. Write none if you have none."),
    CvQuestion("languages", "Which languages do you speak?", "Give an honest level for speaking, reading and writing.")
)

@Composable
fun CvApplicationStudio(context: Context) {
    val store = remember(context) { LocalProfileStore(context) }
    val modelManager = remember(context) { LocalModelManager(context) }
    val scope = rememberCoroutineScope()
    var profile by remember(context) { mutableStateOf(store.load() ?: JSONObject()) }
    var criteria by remember { mutableStateOf("") }
    var targetRole by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Your details stay on this phone. Start with the vacancy you want to target.") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var modelReady by remember { mutableStateOf(false) }
    var followUps by remember { mutableStateOf<List<String>>(emptyList()) }
    var followUpIndex by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf("") }
    var documentKind by remember { mutableStateOf("CV") }
    var draftTitle by remember { mutableStateOf("Tailored CV") }
    var savedDrafts by remember { mutableStateOf(loadCvDrafts(profile)) }
    val missing = CvCoreQuestions.firstOrNull { profile.optString(it.key).isBlank() }
    val currentFollowUp = followUps.getOrNull(followUpIndex)

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) {
            status = "PDF export cancelled."
        } else {
            val textToExport = draft
            val title = draftTitle
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { ApplicationPdfGenerator.write(context, uri, title, textToExport) }
                    status = "PDF saved. Open and review the document before sharing it."
                } catch (e: Exception) {
                    status = "PDF export failed: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    fun saveProfile(updated: JSONObject) {
        store.save(updated.toString())
        profile = updated
    }

    suspend fun ensureModel() {
        withContext(Dispatchers.IO) {
            if (!modelManager.verifyInstalled()) {
                modelManager.deleteModel()
                modelManager.downloadModel { value -> progress = value }
            }
            check(NativeLlama.loadModel(modelManager.modelFile.absolutePath, 4096, 4)) {
                "The local model could not be loaded. Check free RAM and storage."
            }
        }
        modelReady = true
    }

    fun runLocalTask(message: String, action: suspend () -> Unit) {
        if (criteria.isBlank()) {
            status = "Add the target job description or selection criteria first."
            return
        }
        busy = true
        progress = 0
        status = message
        scope.launch {
            try {
                ensureModel()
                action()
            } catch (e: Exception) {
                status = "Could not complete this step: ${e.message ?: e.javaClass.simpleName}"
            } catch (e: UnsatisfiedLinkError) {
                status = "The native local AI runtime is unavailable: ${e.message ?: "library load failed"}"
            } finally {
                busy = false
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = FieldbookPalette.Paper),
        border = BorderStroke(1.dp, FieldbookPalette.Rule)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.background(FieldbookPalette.PaperShade, CircleShape).padding(horizontal = 11.dp, vertical = 7.dp)) {
                    Text("YOUR PRIVATE WORKSHOP", color = FieldbookPalette.Forest, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text("ON DEVICE", color = FieldbookPalette.Rust, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Text("A CV that feels like you.", fontSize = 27.sp, lineHeight = 31.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = FieldbookPalette.Ink)
            Text("Tell us about your real experience. Your local AI will shape it around the job you want — without inventing qualifications or work history.", color = FieldbookPalette.Muted, fontSize = 12.sp, lineHeight = 18.sp)

            Surface(color = FieldbookPalette.Canvas, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("01  ·  THE OPPORTUNITY", color = FieldbookPalette.Rust, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    OutlinedTextField(
                        value = targetRole,
                        onValueChange = { targetRole = it },
                        label = { Text("Job title (e.g. Heavy Diesel Mechanic)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = criteria,
                        onValueChange = { criteria = it },
                        label = { Text("Paste job description / employer criteria") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Text("The job criteria are used only on this device by your downloaded model.", color = FieldbookPalette.Muted, fontSize = 10.sp)
                }
            }

            Surface(color = FieldbookPalette.Canvas, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("02  ·  YOUR STORY", color = FieldbookPalette.Rust, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    val completed = CvCoreQuestions.count { profile.optString(it.key).isNotBlank() }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Candidate profile", color = FieldbookPalette.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("$completed / ${CvCoreQuestions.size}", color = FieldbookPalette.Forest, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    LinearProgressIndicator(
                        progress = { completed.toFloat() / CvCoreQuestions.size.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = FieldbookPalette.Forest,
                        trackColor = FieldbookPalette.PaperShade
                    )
                    when {
                        missing != null -> {
                            Text("A little about you", color = FieldbookPalette.Ink, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(missing.prompt, color = FieldbookPalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(missing.helper, color = FieldbookPalette.Muted, fontSize = 11.sp)
                            OutlinedTextField(
                                value = answer,
                                onValueChange = { answer = it },
                                label = { Text("Your answer") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                shape = RoundedCornerShape(14.dp)
                            )
                            Button(
                                onClick = {
                                    val updated = JSONObject(profile.toString()).put(missing.key, answer.trim())
                                    runCatching { saveProfile(updated); answer = ""; status = "Saved privately. Your next question is ready." }
                                        .onFailure { status = "Could not save this answer: ${it.message}" }
                                },
                                enabled = answer.isNotBlank() && !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save and continue") }
                            TextButton(
                                onClick = {
                                    val updated = JSONObject(profile.toString()).put(missing.key, "Not provided")
                                    runCatching { saveProfile(updated); answer = ""; status = "Skipped. The CV will not claim this information." }
                                        .onFailure { status = "Could not save this choice: ${it.message}" }
                                },
                                enabled = !busy,
                                modifier = Modifier.align(Alignment.End)
                            ) { Text("I don't have this information") }
                        }
                        currentFollowUp != null -> {
                            Text("A question for this job", color = FieldbookPalette.Ink, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text("QUESTION ${followUpIndex + 1} OF ${followUps.size}", color = FieldbookPalette.Rust, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(currentFollowUp, color = FieldbookPalette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            OutlinedTextField(answer, { answer = it }, label = { Text("Your answer") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp))
                            Button(
                                onClick = {
                                    val updated = JSONObject(profile.toString()).put("cv_followup_$followUpIndex", answer.trim())
                                    saveProfile(updated)
                                    answer = ""
                                    followUpIndex += 1
                                    status = "Saved. Moving to the next job-specific question."
                                },
                                enabled = answer.isNotBlank() && !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save and continue") }
                            TextButton(onClick = { followUpIndex += 1; answer = "" }, enabled = !busy, modifier = Modifier.align(Alignment.End)) { Text("Skip this question") }
                        }
                        else -> {
                            Text("Your story is ready for the next step.", color = FieldbookPalette.Forest, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("We have the essential profile details. Ask the local AI to find job-specific gaps before drafting.", color = FieldbookPalette.Muted, fontSize = 11.sp)
                            Button(
                                onClick = {
                                    runLocalTask("Checking the vacancy and preparing a few useful follow-up questions…") {
                                        val evidence = JSONObject(profile.toString()).toString().take(5000)
                                        val output = withContext(Dispatchers.IO) {
                                            LocalAgentOrchestrator().execute(
                                                PersonalAgent.CV_ANALYST,
                                                "Read the vacancy criteria and candidate profile. Return ONLY a JSON array of 0 to 5 short, specific questions that would materially improve this CV. Ask only about facts not already supplied: relevant tasks, equipment, measurable achievements, safety tickets, licences, availability or work rights when relevant. Never suggest the candidate has a credential. Format: [\"question one\",\"question two\"]. If no extra facts are needed return []. Vacancy: $criteria",
                                                evidence,
                                                criteria.take(5000),
                                                300
                                            )
                                        }
                                        val questions = runCatching {
                                            val array = JSONArray(output.substring(output.indexOf('['), output.lastIndexOf(']') + 1))
                                            (0 until array.length()).map { array.optString(it).trim() }.filter { it.length in 8..240 }.take(5)
                                        }.getOrDefault(emptyList())
                                        followUps = questions
                                        followUpIndex = 0
                                        status = if (questions.isEmpty()) "No extra questions were returned. You can create your tailored draft now." else "The AI found ${questions.size} extra details that may strengthen this application."
                                    }
                                },
                                enabled = criteria.isNotBlank() && !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Review my fit and ask follow-up questions") }
                        }
                    }
                }
            }

            Surface(color = FieldbookPalette.PaperShade.copy(alpha = 0.55f), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("03  ·  YOUR TAILORED DOCUMENT", color = FieldbookPalette.Rust, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Text("Thoughtful, honest, job-ready.", fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = FieldbookPalette.Ink)
                    Text("Choose the document you need. The model is downloaded automatically the first time and runs privately on this phone.", color = FieldbookPalette.Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = documentKind == "CV", onClick = { documentKind = "CV" }, label = { Text("Tailored CV") })
                        FilterChip(selected = documentKind == "Cover Letter", onClick = { documentKind = "Cover Letter" }, label = { Text("Cover letter") })
                    }
                    Button(
                        onClick = {
                            runLocalTask("Preparing your private model and writing a CV for this role…") {
                                val allFacts = JSONObject(profile.toString()).apply {
                                    put("target_role", targetRole)
                                    put("job_specific_follow_up_answers", JSONObject().apply {
                                        followUps.indices.forEach { index ->
                                            val value = profile.optString("cv_followup_$index")
                                            if (value.isNotBlank()) put("question_${index + 1}", value)
                                        }
                                    })
                                }.toString().take(5000)
                                val output = withContext(Dispatchers.IO) {
                                    val agent = if (documentKind == "CV") PersonalAgent.CV_TAILOR else PersonalAgent.COVER_LETTER
                                    val instruction = if (documentKind == "CV") {
                                        "Create a polished, ATS-readable CV with clear headings and bullet points. Prioritize vacancy criteria and map each requirement to truthful candidate evidence. Never invent employers, dates, qualifications, licences, work rights, achievements or experience. Mark material gaps as [CANDIDATE TO CONFIRM]. Target role: ${targetRole.ifBlank { "As described in the vacancy" }}. Vacancy criteria:\n$criteria"
                                    } else {
                                        "Write a warm, professional, concise cover letter tailored to this vacancy. Use only candidate facts, explain transferable skills honestly, never invent experience, qualifications, licences or work rights, and mark missing details as [CANDIDATE TO CONFIRM]. Target role: ${targetRole.ifBlank { "As described in the vacancy" }}. Vacancy criteria:\n$criteria"
                                    }
                                    LocalAgentOrchestrator().execute(agent, instruction, allFacts, criteria.take(5000), 512)
                                }
                                draft = output
                                draftTitle = if (targetRole.isBlank()) documentKind else "$documentKind - $targetRole"
                                val updated = JSONObject(profile.toString())
                                val history = updated.optJSONArray("cv_drafts") ?: JSONArray()
                                history.put(JSONObject().put("role", targetRole).put("kind", documentKind).put("criteria", criteria).put("created_at", System.currentTimeMillis()).put("content", output))
                                while (history.length() > 8) history.remove(0)
                                updated.put("cv_drafts", history)
                                saveProfile(updated)
                                savedDrafts = loadCvDrafts(updated)
                                status = "Your draft is ready and saved privately. Check every fact before exporting or sharing."
                            }
                        },
                        enabled = missing == null && currentFollowUp == null && criteria.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Creating your document…" else "Create my tailored $documentKind") }
                    if (busy && progress > 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = FieldbookPalette.Forest, trackColor = FieldbookPalette.PaperShade)
                    if (draft.isNotBlank()) {
                        Text(draft, color = FieldbookPalette.Ink, fontSize = 12.sp, lineHeight = 18.sp)
                        OutlinedButton(onClick = { pdfLauncher.launch("hamari_mehnat_cv.pdf") }, modifier = Modifier.fillMaxWidth()) { Text("Export this CV as PDF") }
                    }
                }
            }

            if (savedDrafts.isNotEmpty()) {
                Surface(color = FieldbookPalette.Canvas, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("YOUR CV LIBRARY", color = FieldbookPalette.Rust, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        savedDrafts.forEachIndexed { index, item ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.optString("role").ifBlank { "Tailored CV" }, color = FieldbookPalette.Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Saved on this device · Version ${index + 1}", color = FieldbookPalette.Muted, fontSize = 10.sp)
                                }
                                TextButton(onClick = {
                                    draft = item.optString("content")
                                    draftTitle = "${item.optString("kind").ifBlank { "CV" }} - ${item.optString("role").ifBlank { "Tailored" }}"
                                    status = "Loaded a saved draft. Review before exporting."
                                }) { Text("Open") }
                            }
                        }
                    }
                }
            }

            if (busy) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = FieldbookPalette.Forest, trackColor = FieldbookPalette.PaperShade)
            Text(status, color = FieldbookPalette.Muted, fontSize = 11.sp, lineHeight = 16.sp)
            Text("Private by design · No automatic applications or employer messages are sent.", color = FieldbookPalette.Forest, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun loadCvDrafts(profile: JSONObject): List<JSONObject> {
    val array = profile.optJSONArray("cv_drafts") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }.reversed()
}
