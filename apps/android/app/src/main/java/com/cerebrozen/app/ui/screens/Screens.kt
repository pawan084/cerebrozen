package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft

/** You: the iOS ProfileView hub — a profile header + nav-row settings list
 * routing to sub-screens, then legal/account actions and sign out. */
@Composable
fun YouScreen(onOpen: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var companion by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            val me = Api.me()
            name = me.optString("name")
            companion = me.optString("companion")
            language = me.optString("language")
        }
    }

    Page("Settings and support", "You") {
        SectionCard {
            Text(name.ifBlank { "You" }, style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(
                "${companion.ifBlank { "Calm Guide" }} · ${language.ifBlank { "English" }}",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            )
        }

        NavRow("Companion style", "${companion.ifBlank { "Calm Guide" }} · how CereBro talks with you",
            icon = Icons.Outlined.ChatBubbleOutline, emphasis = true) { onOpen("companion") }
        NavRow("Daily reminder", "Gentle daily check-in", icon = Icons.Outlined.NotificationsNone) { onOpen("reminders") }
        NavRow("Weekly insights", "Your progress and patterns", icon = Icons.Outlined.Insights) { onOpen("insights") }
        NavRow("Privacy & memory", "Control what CereBro remembers", icon = Icons.Outlined.Lock) { onOpen("privacy") }
        NavRow("Premium plan", "Unlock the full library", icon = Icons.Outlined.WorkspacePremium) { onOpen("premium") }
        NavRow("Urgent support", "Emergency resources", icon = Icons.Outlined.Emergency) { onOpen("crisis") }
        NavRow("Crisis region", "Which helplines to show", icon = Icons.Outlined.Public) { onOpen("crisisregion") }
        NavRow("Human support", "Coach or therapist handoff", icon = Icons.Outlined.Diversity3) { onOpen("humansupport") }

        Text("LEGAL & ACCOUNT", style = MaterialTheme.typography.labelSmall, color = Periwinkle,
            modifier = Modifier.padding(top = 8.dp))
        NavRow("Privacy policy", "How we handle your data", icon = Icons.Outlined.Shield) { onOpen("privacypolicy") }
        NavRow("Export my data", "Download a full copy", icon = Icons.Outlined.FileDownload) { onOpen("export") }
        NavRow("Delete account", "Permanently erase everything", icon = Icons.Outlined.DeleteOutline) { onOpen("delete") }

        TextButton(onClick = { Session.signOut() }) { Text("Sign out", color = TextMuted) }
        Text("Wellness support, not emergency care.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
    }
}
