package com.hamari.mehenat.jobhunter

import android.os.Bundle
import android.content.Context
import com.hamari.mehenat.jobhunter.data.AutoJobSearchScheduler
import com.hamari.mehenat.jobhunter.data.LocalProfileStore
import com.hamari.mehenat.jobhunter.data.EncryptedDocumentVault
import com.hamari.mehenat.jobhunter.data.ApplicationTrackerStore
import com.hamari.mehenat.jobhunter.data.LocalModelManager
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF101318)
private val Panel = Color(0xFF191F27)
private val Gold = Color(0xFFD6B36A)
private val Muted = Color(0xFF9AA4B2)
private val SoftWhite = Color(0xFFF1F3F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoJobSearchScheduler.schedule(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Gold, onPrimary = Ink, background = Ink,
                onBackground = SoftWhite, surface = Panel, onSurface = SoftWhite
            )) { JobHunterApp(applicationContext) }
        }
    }
}

private data class Destination(val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("Home", Icons.Outlined.Home),
    Destination("Jobs", Icons.Outlined.Explore),
    Destination("Files", Icons.Outlined.Description),
    Destination("Tracker", Icons.Outlined.WorkOutline),
    Destination("Local AI", Icons.Outlined.SmartToy)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobHunterApp(appContext: Context) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Ink,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("HAMARI MEHNAT", fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("PERSONAL JOB HUNTER", fontSize = 9.sp, color = Muted, letterSpacing = 1.8.sp)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Ink, titleContentColor = SoftWhite)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                destinations.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(appContext)
                1 -> JobsScreen(appContext)
                2 -> FilesScreen(appContext)
                3 -> TrackerScreen(appContext)
                4 -> LocalAiScreen(appContext)
            }
        }
    }
}

@Composable
private fun HomeScreen(context: Context) {
    val storedProfile = remember { LocalProfileStore(context).load() ?: JSONObject() }
    val documentCount = remember { EncryptedDocumentVault(context).listIds().size }
    val trackedJobs = remember { ApplicationTrackerStore(context).all() }
    val downloadedModel = remember { LocalModelManager(context).isDownloaded() }
    var fullName by remember { mutableStateOf(storedProfile.optString("full_name")) }
    var email by remember { mutableStateOf(storedProfile.optString("email")) }
    var phone by remember { mutableStateOf(storedProfile.optString("phone")) }
    var roles by remember { mutableStateOf(storedProfile.optJSONArray("target_roles").asCsv()) }
    var countries by remember { mutableStateOf(storedProfile.optJSONArray("target_countries").asCsv()) }
    var skills by remember { mutableStateOf(storedProfile.optJSONArray("skills").asCsv()) }
    var experience by remember { mutableStateOf(storedProfile.optString("experience_summary")) }
    var profileMessage by remember { mutableStateOf(if (storedProfile.length() == 0) "Complete your private profile to enable matching and scheduled search." else "Encrypted personal profile loaded.") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your next opportunity.", fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = SoftWhite)
            Text("One private workspace for your international job search.", color = Muted, fontSize = 14.sp)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
            border = BorderStroke(1.dp, Color(0xFF303945))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(9.dp).background(Color(0xFF76A88A), CircleShape))
                    Text("SINGLE-USER PRIVATE BUILD", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
                Text("Built around your profile, not a public job platform.", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("No public accounts • No subscription • No automatic applications", color = Muted, fontSize = 12.sp)
            }
        }
        Text("WORKSPACE", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Profile", if (storedProfile.length() > 0) "Configured" else "Not set up", Modifier.weight(1f))
            MetricCard("Documents", "$documentCount stored", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Saved jobs", "${trackedJobs.size} saved", Modifier.weight(1f))
            MetricCard("Applications", "${trackedJobs.count { it.stage != "Saved" }} active", Modifier.weight(1f))
        }
        Text("BUILD STATUS", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        StatusRow("Android application", "Native Kotlin and Jetpack Compose", true)
        StatusRow("Local AI model runtime", if (downloadedModel) "Model file present; checksum checked on load" else "llama.cpp integrated; model not downloaded", true)
        StatusRow("Encrypted document vault", "Android Keystore AES-GCM • PDF / DOCX / TXT extraction", true)
        StatusRow("Job search and matching engine", "Public feeds • company boards • relevance and sponsorship signals", true)
        StatusRow("Interview and visa intelligence", "Local reminders • official government page fetcher", true)
        Text("PERSONAL PROFILE", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = roles, onValueChange = { roles = it }, label = { Text("Target roles (comma separated)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = countries, onValueChange = { countries = it }, label = { Text("Target countries (comma separated)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = skills, onValueChange = { skills = it }, label = { Text("Skills (comma separated)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = experience, onValueChange = { experience = it }, label = { Text("Experience summary") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Button(onClick = {
            try {
                val split = { value: String -> JSONArray(value.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()) }
                val profile = JSONObject()
                    .put("full_name", fullName.trim())
                    .put("email", email.trim())
                    .put("phone", phone.trim())
                    .put("target_roles", split(roles))
                    .put("target_countries", split(countries))
                    .put("skills", split(skills))
                    .put("experience_summary", experience.trim())
                LocalProfileStore(context).save(profile.toString())
                profileMessage = "Profile saved encrypted on this device. Scheduled job refresh uses your target roles and countries."
            } catch (e: Exception) {
                profileMessage = "Could not save profile: ${e.message ?: e.javaClass.simpleName}"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save encrypted profile") }
        Text(profileMessage, color = Gold, fontSize = 12.sp)
        Text(
            "Privacy first: personal documents stay on your device. Online job search runs only when a connection is available.",
            color = Muted, fontSize = 12.sp, lineHeight = 18.sp
        )
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Color(0xFF303945))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value, color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatusRow(title: String, status: String, complete: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(status, color = Muted, fontSize = 11.sp)
        }
        Text(if (complete) "✓" else "○", color = if (complete) Gold else Muted, fontSize = 18.sp)
    }
}

@Composable
private fun FeatureScreen(title: String, description: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = SoftWhite)
    Text(description, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Color(0xFF303945))
    ) {
        Text(
            "This module is not marked complete until its real storage, permissions, error handling and tests are implemented.",
            Modifier.padding(18.dp), color = Gold, fontSize = 13.sp, lineHeight = 20.sp
        )
    }
}


private fun JSONArray?.asCsv(): String =
    if (this == null) "" else (0 until length()).joinToString(", ") { optString(it) }
