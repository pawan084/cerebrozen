package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm

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

    PremiumPage(stringResource(R.string.you_eyebrow), stringResource(R.string.you_title), trailing = Icons.Outlined.Settings) {
        SectionCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The avatar orb (mirrors iOS ProfileView's gradient orb).
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(
                        Brush.radialGradient(listOf(Color.White, TextSoft, Periwinkle)),
                    ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name.ifBlank { stringResource(R.string.you_default_name) }, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(
                        // "Calm Guide"/"English" are server-profile fallback values
                        // (cross-stack contract), so they stay literal for now.
                        "${companion.ifBlank { "Calm Guide" }} · ${language.ifBlank { "English" }}",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
            }
        }

        // The persistent Support door (REDESIGN §2.3): calm, visually distinct,
        // and always two taps from anywhere — never a scare button.
        SectionCard(onClick = { onOpen("crisis") }) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.HealthAndSafety, contentDescription = null,
                    tint = Warm, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.you_support_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(stringResource(R.string.crisis_support_line),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
                Text("›", style = MaterialTheme.typography.titleMedium, color = TextMuted2)
            }
        }

        PremiumNavRow(stringResource(R.string.you_companion_title),
            stringResource(R.string.you_companion_subtitle, companion.ifBlank { "Calm Guide" }),
            icon = Icons.Outlined.ChatBubbleOutline, emphasis = true) { onOpen("companion") }
        PremiumNavRow(stringResource(R.string.you_appearance_title), stringResource(R.string.you_appearance_subtitle),
            icon = Icons.Outlined.DarkMode) { onOpen("appearance") }
        PremiumNavRow(stringResource(R.string.you_reminder_title), stringResource(R.string.you_reminder_subtitle),
            icon = Icons.Outlined.NotificationsNone) { onOpen("reminders") }
        PremiumNavRow(stringResource(R.string.you_insights_title), stringResource(R.string.you_insights_subtitle),
            icon = Icons.Outlined.Insights) { onOpen("insights") }
        PremiumNavRow(stringResource(R.string.you_privacy_title), stringResource(R.string.privacy_control_line),
            icon = Icons.Outlined.Lock) { onOpen("privacy") }
        PremiumNavRow(stringResource(R.string.you_crisisregion_title), stringResource(R.string.you_crisisregion_subtitle),
            icon = Icons.Outlined.Public) { onOpen("crisisregion") }
        PremiumNavRow(stringResource(R.string.humansupport_title), stringResource(R.string.you_humansupport_subtitle),
            icon = Icons.Outlined.Diversity3) { onOpen("humansupport") }

        Text(stringResource(R.string.you_legal_header), style = MaterialTheme.typography.labelSmall, color = Periwinkle,
            modifier = Modifier.padding(top = 8.dp))
        PremiumNavRow(stringResource(R.string.privacypolicy_title), stringResource(R.string.privacypolicy_eyebrow),
            icon = Icons.Outlined.Shield) { onOpen("privacypolicy") }
        PremiumNavRow(stringResource(R.string.export_title), stringResource(R.string.you_export_subtitle),
            icon = Icons.Outlined.FileDownload) { onOpen("export") }
        PremiumNavRow(stringResource(R.string.delete_title), stringResource(R.string.you_delete_subtitle),
            icon = Icons.Outlined.DeleteOutline) { onOpen("delete") }

        TextButton(onClick = { Session.signOut() }) { Text(stringResource(R.string.you_signout), color = TextMuted) }
        Text(stringResource(R.string.common_wellness_footer),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
    }
}
