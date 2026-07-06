package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONObject

private val CONSENT_KEYS = listOf(
    "mood_history" to "Mood history",
    "ai_memory" to "AI memory",
    "journal_memory" to "Journal memory",
    "sleep_history" to "Sleep history",
    "voice_storage" to "Voice clips",
    "model_training" to "Help improve models",
)

/** You: account + live consent toggles + data export/delete + crisis + sign out. */
@Composable
fun YouScreen(onOpen: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val consent = remember { mutableStateMapOf<String, Boolean>() }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { val me = Api.me(); name = me.optString("name"); email = me.optString("email") }
        runCatching { val c = Api.consent(); CONSENT_KEYS.forEach { consent[it.first] = c.optBoolean(it.first) } }
    }

    Page("Settings and support", "You") {
        SectionCard {
            Text(name.ifBlank { "Your account" }, style = MaterialTheme.typography.titleMedium, color = TextSoft)
            if (email.isNotBlank()) Text(email, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }

        SectionCard {
            Text("Privacy & memory", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text("Control what CereBro remembers — changes save instantly.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            CONSENT_KEYS.forEach { (key, label) ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                    Switch(
                        checked = consent[key] == true,
                        onCheckedChange = { v ->
                            consent[key] = v
                            scope.launch { runCatching { Api.updateConsent(JSONObject().put(key, v)) } }
                        },
                    )
                }
            }
        }

        SectionCard {
            Text("Your data", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text("Export a full copy, or delete everything — your call, any time.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            TextButton(
                onClick = {
                    scope.launch {
                        runCatching { Api.exportData() }
                            .onSuccess { status = "Exported ${it.length} characters of your data." }
                            .onFailure { status = it.message ?: "Export failed." }
                    }
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("Export my data", color = Periwinkle) }

            if (!confirmDelete) {
                TextButton(
                    onClick = { confirmDelete = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Text("Delete my account", color = Danger) }
            } else {
                Text("This permanently erases your account and data.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        scope.launch { runCatching { Api.deleteAccount() }; Session.signOut() }
                    }) { Text("Delete forever") }
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextMuted) }
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        }

        SectionCard {
            Column(Modifier.fillMaxWidth().clickable { onOpen("crisis") },
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Urgent support", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text("Locale-aware crisis resources, always a tap away.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }

        TextButton(onClick = { Session.signOut() },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("Sign out", color = TextMuted)
        }
        Text("Wellness support, not emergency care.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}
