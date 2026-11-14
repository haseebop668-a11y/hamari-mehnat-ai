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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoJobSearchScheduler.schedule(this)
        setContent {
            HamariMehnatTheme {
                JobHunterApp(applicationContext)
            }
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

@Composable
private fun JobHunterApp(appContext: Context) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = FieldbookPalette.Canvas,
        topBar = { PassportTopBar() },
        bottomBar = {
            Surface(
                color = FieldbookPalette.Canvas,
                shadowElevation = 12.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEachIndexed { index, item ->
                        val selected = selectedTab == index
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(15.dp))
                                .background(if (selected) FieldbookPalette.Forest else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = if (selected) FieldbookPalette.White else FieldbookPalette.Muted,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                item.label,
                                color = if (selected) FieldbookPalette.White else FieldbookPalette.Muted,
                                fontSize = 9.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(appContext) { selectedTab = it }
                1 -> JobsScreen(appContext)
                2 -> FilesScreen(appContext)
                3 -> TrackerScreen(appContext)
                4 -> LocalAiScreen(appContext)
            }
            Text(
                "HAMARI MEHNAT  •  PRIVATE BY DESIGN",
                color = FieldbookPalette.Muted,
                fontSize = 9.sp,
                letterSpacing = 1.1.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun PassportTopBar() {
    Surface(color = FieldbookPalette.Canvas) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    Modifier
                        .size(43.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(FieldbookPalette.Forest),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "HM",
                        color = FieldbookPalette.White,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "HAMARI MEHNAT",
                        color = FieldbookPalette.Ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 1.25.sp
                    )
                    Text(
                        "YOUR PRIVATE WORK PASSPORT",
                        color = FieldbookPalette.Muted,
                        fontSize = 8.sp,
                        letterSpacing = 1.35.sp
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(FieldbookPalette.Paper)
                            .border(1.dp, FieldbookPalette.Rule, RoundedCornerShape(20.dp))
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(6.dp).background(FieldbookPalette.Forest, CircleShape))
                        Text("PRIVATE", color = FieldbookPalette.Forest, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                    }
                    Text("LOCAL-FIRST", color = FieldbookPalette.Muted, fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }
            HorizontalDivider(color = FieldbookPalette.Rule, thickness = 1.dp)
        }
    }
}

@Composable
private fun HomeScreen(context: Context, onNavigate: (Int) -> Unit) {
    val storedProfile = remember { LocalProfileStore(context).load() ?: JSONObject() }
    val trackedJobs = remember { ApplicationTrackerStore(context).all() }
    val documentCount = remember { EncryptedDocumentVault(context).listIds().size }
    val modelPresent = remember { LocalModelManager(context).isDownloaded() }

    var fullName by remember { mutableStateOf(storedProfile.optString("full_name")) }
    var email by remember { mutableStateOf(storedProfile.optString("email")) }
    var phone by remember { mutableStateOf(storedProfile.optString("phone")) }
    var roles by remember { mutableStateOf(storedProfile.optJSONArray("target_roles").asCsv()) }
    var countries by remember { mutableStateOf(storedProfile.optJSONArray("target_countries").asCsv()) }
    var skills by remember { mutableStateOf(storedProfile.optJSONArray("skills").asCsv()) }
    var experience by remember { mutableStateOf(storedProfile.optString("experience_summary")) }
    var profileExpanded by rememberSaveable { mutableStateOf(storedProfile.length() == 0) }
    var profileMessage by remember {
        mutableStateOf(
            if (storedProfile.length() == 0) "A few details help the matcher find relevant work."
            else "Your profile is encrypted and stored on this device."
        )
    }

    val savedCount = trackedJobs.size
    val activeCount = trackedJobs.count { it.stage != "Saved" }
    val profileReady = roles.isNotBlank() && countries.isNotBlank()
    val greeting = fullName.trim().substringBefore(" ").ifBlank { "there" }

    Column(verticalArrangement = Arrangement.spacedBy(19.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                "FIELD DESK  /  01",
                color = FieldbookPalette.Rust,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.7.sp
            )
            Text(
                "Welcome back, $greeting.",
                color = FieldbookPalette.Ink,
                fontFamily = FontFamily.Serif,
                fontSize = 31.sp,
                lineHeight = 37.sp
            )
            Text(
                "A considered route from your skills to your next opportunity.",
                color = FieldbookPalette.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(containerColor = FieldbookPalette.ForestDeep),
            border = BorderStroke(1.dp, FieldbookPalette.Forest)
        ) {
            Column(
                Modifier.padding(19.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PERSONAL WORK PASSPORT",
                        color = Color(0xFFD9C8A4),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.55.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text("VOL. 01", color = Color(0xFFB6C9BE), fontSize = 9.sp, letterSpacing = 1.2.sp)
                }
                Text(
                    "Make your next move\ncount.",
                    color = FieldbookPalette.White,
                    fontFamily = FontFamily.Serif,
                    fontSize = 29.sp,
                    lineHeight = 34.sp
                )
                Text(
                    "One private place to discover work, check the details and prepare a truthful application.",
                    color = Color(0xFFD3DDD6),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                HorizontalDivider(color = Color(0xFF49685E))
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    PassportStat("05", "COUNTRY ROUTES", Modifier.weight(1f))
                    PassportStat("04", "PUBLIC FEEDS", Modifier.weight(1f))
                    PassportStat("01", "PRIVATE VAULT", Modifier.weight(1f))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("AT A GLANCE", "Your workspace")
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricTile("Saved jobs", savedCount.toString(), "Ready to review", Modifier.weight(1f))
                MetricTile("In progress", activeCount.toString(), "Applications", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricTile("Documents", documentCount.toString(), "Encrypted files", Modifier.weight(1f))
                MetricTile("Local AI", if (modelPresent) "Model file" else "Not set up", if (modelPresent) "Ready for checksum check" else "Download when needed", Modifier.weight(1f))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("YOUR ROUTE", "Four useful steps")
            RouteStep("01", "Set your direction", if (profileReady) "Target roles and countries are saved." else "Add target roles and countries to your profile.", profileReady)
            RouteStep("02", "Find the right opening", if (savedCount > 0) "$savedCount vacancy/vacancies saved for review." else "Search public feeds and compare the original listings.", savedCount > 0)
            RouteStep("03", "Prepare your evidence", if (documentCount > 0) "$documentCount private document(s) in your vault." else "Keep your CV and supporting documents in the encrypted vault.", documentCount > 0)
            RouteStep("04", "Follow every application", if (activeCount > 0) "$activeCount application(s) moved beyond Saved." else "Update stages and keep employer notes in your tracker.", activeCount > 0)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("SHORTCUTS", "Pick up where you need")
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                WorkActionCard("Explore work", "Public job feeds", Icons.Outlined.Explore, Modifier.weight(1f)) { onNavigate(1) }
                WorkActionCard("Open vault", "CVs and documents", Icons.Outlined.Description, Modifier.weight(1f)) { onNavigate(2) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                WorkActionCard("Track progress", "Applications and interviews", Icons.Outlined.WorkOutline, Modifier.weight(1f)) { onNavigate(3) }
                WorkActionCard("Local assistant", "Private on-device AI", Icons.Outlined.SmartToy, Modifier.weight(1f)) { onNavigate(4) }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(21.dp),
            colors = CardDefaults.cardColors(containerColor = FieldbookPalette.Paper),
            border = BorderStroke(1.dp, FieldbookPalette.Rule)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("CANDIDATE FILE", color = FieldbookPalette.Rust, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text("Your profile", color = FieldbookPalette.Ink, fontFamily = FontFamily.Serif, fontSize = 22.sp)
                    }
                    TextButton(onClick = { profileExpanded = !profileExpanded }) {
                        Text(if (profileExpanded) "Close" else "Edit profile")
                    }
                }
                Text(
                    if (profileReady) "Search direction is configured. Keep your skills and experience current."
                    else "Set your target roles and countries first. You can add the rest whenever you're ready.",
                    color = FieldbookPalette.Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                if (profileExpanded) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = roles, onValueChange = { roles = it }, label = { Text("Target roles (comma separated)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(value = countries, onValueChange = { countries = it }, label = { Text("Target countries (comma separated)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(value = skills, onValueChange = { skills = it }, label = { Text("Skills (comma separated)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(value = experience, onValueChange = { experience = it }, label = { Text("Experience summary") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    Button(
                        onClick = {
                            try {
                                val split = { value: String ->
                                    JSONArray(value.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct())
                                }
                                val profile = JSONObject()
                                    .put("full_name", fullName.trim())
                                    .put("email", email.trim())
                                    .put("phone", phone.trim())
                                    .put("target_roles", split(roles))
                                    .put("target_countries", split(countries))
                                    .put("skills", split(skills))
                                    .put("experience_summary", experience.trim())
                                LocalProfileStore(context).save(profile.toString())
                                profileMessage = "Profile saved encrypted on this device."
                                profileExpanded = false
                            } catch (e: Exception) {
                                profileMessage = "Could not save profile: ${e.message ?: e.javaClass.simpleName}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save private profile") }
                }
                Text(profileMessage, color = FieldbookPalette.Forest, fontSize = 11.sp)
                Text(
                    "No public account • No cloud CV storage • No automatic applications",
                    color = FieldbookPalette.Muted,
                    fontSize = 9.sp,
                    letterSpacing = 0.35.sp
                )
            }
        }
    }
}

@Composable
private fun PassportStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, color = FieldbookPalette.White, fontFamily = FontFamily.Serif, fontSize = 22.sp)
        Text(label, color = Color(0xFFB6C9BE), fontSize = 8.sp, letterSpacing = 0.55.sp)
    }
}

@Composable
private fun SectionHeading(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(eyebrow, color = FieldbookPalette.Rust, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(title, color = FieldbookPalette.Ink, fontFamily = FontFamily.Serif, fontSize = 22.sp)
    }
}

@Composable
private fun MetricTile(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(FieldbookPalette.Paper)
            .border(1.dp, FieldbookPalette.Rule, RoundedCornerShape(18.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(title, color = FieldbookPalette.Muted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(value, color = FieldbookPalette.Ink, fontFamily = FontFamily.Serif, fontSize = 21.sp, maxLines = 1)
        Text(detail, color = FieldbookPalette.Muted, fontSize = 9.sp, lineHeight = 13.sp)
    }
}

@Composable
private fun RouteStep(number: String, title: String, detail: String, complete: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(35.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (complete) FieldbookPalette.Forest else FieldbookPalette.PaperShade)
                .border(1.dp, if (complete) FieldbookPalette.Forest else FieldbookPalette.Rule, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = if (complete) FieldbookPalette.White else FieldbookPalette.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = FieldbookPalette.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = FieldbookPalette.Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Text(if (complete) "DONE" else "NEXT", color = if (complete) FieldbookPalette.Forest else FieldbookPalette.Rust, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun WorkActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(FieldbookPalette.Paper)
            .border(1.dp, FieldbookPalette.Rule, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(FieldbookPalette.PaperShade),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = FieldbookPalette.Forest, modifier = Modifier.size(19.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = FieldbookPalette.Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(subtitle, color = FieldbookPalette.Muted, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

private fun JSONArray?.asCsv(): String =
    if (this == null) "" else (0 until length()).joinToString(", ") { optString(it) }
