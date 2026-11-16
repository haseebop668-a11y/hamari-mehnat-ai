package com.hamari.mehenat.jobhunter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamari.mehenat.jobhunter.data.AdzunaCredentialsStore
import com.hamari.mehenat.jobhunter.data.ConnectorPreferences

@Composable
fun ConnectorHub(
    context: Context,
    greenhouseBoards: String,
    onGreenhouseBoardsChange: (String) -> Unit,
    leverSites: String,
    onLeverSitesChange: (String) -> Unit
) {
    val preferences = remember(context) { ConnectorPreferences(context) }
    val credentialStore = remember(context) { AdzunaCredentialsStore(context) }
    var enabledIds by remember { mutableStateOf(preferences.enabledIds()) }
    var adzunaAppId by remember { mutableStateOf("") }
    var adzunaAppKey by remember { mutableStateOf("") }
    var adzunaConnected by remember { mutableStateOf(credentialStore.load() != null) }
    var message by remember { mutableStateOf("Choose which job sources this app may query. Your choices stay on this phone.") }

    fun setEnabled(id: String, enabled: Boolean) {
        preferences.setEnabled(id, enabled)
        enabledIds = preferences.enabledIds()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FieldbookPalette.Paper),
        border = BorderStroke(1.dp, FieldbookPalette.Rule)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CONNECTOR HUB", color = FieldbookPalette.Rust, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text("Your job-search connections", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            Text("Public feeds connect without an account. Authenticated providers use the provider's supported authorization method; this app never asks for your job-board password.", color = FieldbookPalette.Muted, fontSize = 11.sp)

            ConnectorToggle("Arbeitnow", "Public job feed · no login", "arbeitnow" in enabledIds, { setEnabled("arbeitnow", it) })
            ConnectorToggle("Remotive", "Public remote-job feed · no login", "remotive" in enabledIds, { setEnabled("remotive", it) })
            ConnectorToggle("Himalayas", "Public remote-job feed · no login", "himalayas" in enabledIds, { setEnabled("himalayas", it) })
            ConnectorToggle("Jobicy", "Public job feed · no login", "jobicy" in enabledIds, { setEnabled("jobicy", it) })

            androidx.compose.material3.HorizontalDivider(color = FieldbookPalette.Rule)
            Text("ADZUNA", fontWeight = FontWeight.SemiBold)
            Text(if (adzunaConnected) "API credentials are encrypted with Android Keystore on this device." else "Optional authenticated API · AU, NZ, UK, DE and CA. Adzuna currently issues app ID/key credentials, not a universal OAuth login.", color = FieldbookPalette.Muted, fontSize = 11.sp)
            if (!adzunaConnected) {
                OutlinedTextField(adzunaAppId, { adzunaAppId = it }, label = { Text("Adzuna app ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(adzunaAppKey, { adzunaAppKey = it }, label = { Text("Adzuna app key") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                Button(
                    onClick = {
                        runCatching { credentialStore.save(adzunaAppId, adzunaAppKey) }
                            .onSuccess {
                                adzunaConnected = true
                                adzunaAppId = ""
                                adzunaAppKey = ""
                                setEnabled("adzuna", true)
                                message = "Adzuna connected. Credentials are encrypted locally."
                            }
                            .onFailure { message = "Could not connect Adzuna: ${it.message ?: it.javaClass.simpleName}" }
                    },
                    enabled = adzunaAppId.isNotBlank() && adzunaAppKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Connect Adzuna") }
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.adzuna.com/")))
                }) { Text("Open Adzuna developer login") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connected", color = FieldbookPalette.Forest, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(checked = "adzuna" in enabledIds, onCheckedChange = { setEnabled("adzuna", it) })
                }
                OutlinedButton(onClick = {
                    runCatching { credentialStore.clear() }
                        .onSuccess {
                            adzunaConnected = false
                            setEnabled("adzuna", false)
                            message = "Adzuna disconnected and its local encryption key removed."
                        }
                        .onFailure { message = "Disconnect failed: ${it.message ?: it.javaClass.simpleName}" }
                }) { Text("Disconnect Adzuna") }
            }

            androidx.compose.material3.HorizontalDivider(color = FieldbookPalette.Rule)
            Text("COMPANY ATS BOARDS", fontWeight = FontWeight.SemiBold)
            Text("Greenhouse and Lever are public company-board endpoints. Add board IDs/site names; these do not use a personal account login.", color = FieldbookPalette.Muted, fontSize = 11.sp)
            OutlinedTextField(greenhouseBoards, onGreenhouseBoardsChange, label = { Text("Greenhouse board IDs (comma separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(leverSites, onLeverSitesChange, label = { Text("Lever site names (comma separated)") }, modifier = Modifier.fillMaxWidth())
            ConnectorToggle("Greenhouse boards", if (greenhouseBoards.isBlank()) "Enter board IDs above before enabling" else "Company-specific public ATS feed", "greenhouse" in enabledIds, { enabled ->
                if (enabled && greenhouseBoards.isBlank()) message = "Add at least one Greenhouse board ID before enabling this connector."
                else setEnabled("greenhouse", enabled)
            })
            ConnectorToggle("Lever boards", if (leverSites.isBlank()) "Enter site names above before enabling" else "Company-specific public ATS feed", "lever" in enabledIds, { enabled ->
                if (enabled && leverSites.isBlank()) message = "Add at least one Lever site name before enabling this connector."
                else setEnabled("lever", enabled)
            })
            Text("LinkedIn, Indeed, SEEK and EURES remain browser hand-offs, not account-connected API integrations. Use their official sites directly; this app does not scrape private accounts.", color = FieldbookPalette.Muted, fontSize = 10.sp)
            Text(message, color = FieldbookPalette.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ConnectorToggle(
    name: String,
    detail: String,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = FieldbookPalette.Muted, fontSize = 10.sp)
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}
