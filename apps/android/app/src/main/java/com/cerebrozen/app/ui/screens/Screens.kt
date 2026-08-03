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
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.Explore
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.AlertDialog
import com.cerebrozen.app.ui.theme.Danger
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PersonAddAlt
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
    var signOutAsked by remember { mutableStateOf(false) }

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
                        // Known taxonomy values localize for DISPLAY; the
                        // server-side values stay English (contract).
                        run {
                            val comp = companion.ifBlank { "Calm Guide" }
                            val lang = language.ifBlank { "English" }
                            val compLabel = companionLabelRes(comp)?.let { stringResource(it) } ?: comp
                            val langLabel = languageLabelRes(lang)?.let { stringResource(it) } ?: lang
                            "$compLabel · $langLabel"
                        },
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
                    Text(stringResource(R.string.crisis_telemanas_line),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
                // The same AutoMirrored chevron every NavRow on this screen uses.
                // This one was a literal "›" glyph at a different size and colour,
                // and it does not mirror in RTL.
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextMuted2,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        PremiumNavRow(stringResource(R.string.you_companion_title),
            stringResource(R.string.you_companion_subtitle, run {
                val comp = companion.ifBlank { "Calm Guide" }
                companionLabelRes(comp)?.let { stringResource(it) } ?: comp
            }),
            icon = Icons.Outlined.ChatBubbleOutline, emphasis = true) { onOpen("companion") }
        PremiumNavRow(stringResource(R.string.you_appearance_title), stringResource(R.string.you_appearance_subtitle),
            icon = Icons.Outlined.DarkMode) { onOpen("appearance") }
        PremiumNavRow(stringResource(R.string.you_reminder_title), stringResource(R.string.you_reminder_subtitle),
            icon = Icons.Outlined.NotificationsNone) { onOpen("reminders") }
        // Re-run the four-stop Home tour (iOS parity). Clears only `tour_done`
        // and lands on Home, where the overlay re-arms itself.
        PremiumNavRow(stringResource(R.string.you_tour_title), stringResource(R.string.you_tour_subtitle),
            icon = Icons.Outlined.Explore) { TourState.reset(); onOpen("home") }
        // The one screen the user fills in rather than reads.
        PremiumNavRow(stringResource(R.string.you_goals_title), stringResource(R.string.you_goals_subtitle),
            icon = Icons.Outlined.Flag) { onOpen("goals") }
        PremiumNavRow(stringResource(R.string.you_insights_title), stringResource(R.string.you_insights_subtitle),
            icon = Icons.Outlined.Insights) { onOpen("insights") }
<<<<<<< HEAD
        // Trends sits beside Insights, not inside it: Insights is what the app
        // has to say, Trends is what the user's own entries look like. They
        // answer different questions and one should not be buried in the other.
        NavRow(stringResource(R.string.you_trends_title), stringResource(R.string.you_trends_subtitle),
            icon = Icons.Outlined.ShowChart) { onOpen("trends") }
        NavRow(stringResource(R.string.you_privacy_title), stringResource(R.string.privacy_control_line),
=======
        PremiumNavRow(stringResource(R.string.you_privacy_title), stringResource(R.string.privacy_control_line),
>>>>>>> 2869a68cd34702cd622e3c8e661d57347757bd58
            icon = Icons.Outlined.Lock) { onOpen("privacy") }
        PremiumNavRow(stringResource(R.string.you_patterns_title), stringResource(R.string.you_patterns_subtitle),
            icon = Icons.Outlined.Psychology) { onOpen("patterns") }
        PremiumNavRow(stringResource(R.string.you_safetyplan_title), stringResource(R.string.you_safetyplan_subtitle),
            icon = Icons.Outlined.Shield) { onOpen("safetyplan") }
        // The Crisis screen's "add one in Settings" now has a Settings to mean.
        PremiumNavRow(stringResource(R.string.trusted_title), stringResource(R.string.you_trusted_subtitle),
            icon = Icons.Outlined.PersonAddAlt) { onOpen("trustedcontact") }
        // The one upsell surface carries an occasional sheen (iOS parity) — the
        // rest of You stays still, which is what makes this row read as the
        // offer rather than as another setting.
        Box(Modifier.sheen()) {
            PremiumNavRow(stringResource(R.string.you_premium_title), stringResource(R.string.you_premium_subtitle),
                icon = Icons.Outlined.WorkspacePremium) { onOpen("premium") }
        }
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
        // Deliberately NOT a PremiumNavRow like every row above it: as a premium
        // row, "Delete account · Permanently erase everything" was pixel-identical
        // to "Privacy policy · How we handle your data", so the single most
        // irreversible action in the app looked like a link to a document. The
        // danger tint is the difference (2026-07-31 module audit).
        NavRow(stringResource(R.string.delete_title), stringResource(R.string.you_delete_subtitle),
            icon = Icons.Outlined.DeleteOutline, tint = Danger) { onOpen("delete") }

        // Sign out was a bare TextButton in TextMuted — a caption, on a screen
        // where every other action is a bordered card — and it signed you out on
        // the first tap. Session.signOut() clears the local store, so an
        // accidental tap took the cached reads and any unsaved draft with it.
        // Now a row that looks like a control, and one question first.
        NavRow(
            stringResource(R.string.you_signout),
            stringResource(R.string.you_signout_subtitle),
            icon = Icons.AutoMirrored.Outlined.Logout,
        ) { signOutAsked = true }
        if (signOutAsked) {
            AlertDialog(
                onDismissRequest = { signOutAsked = false },
                title = { Text(stringResource(R.string.you_signout_confirm_title)) },
                text = { Text(stringResource(R.string.you_signout_confirm_body)) },
                confirmButton = {
                    TextButton(onClick = { signOutAsked = false; Session.signOut() }) {
                        Text(stringResource(R.string.you_signout), color = Danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { signOutAsked = false }) {
                        Text(stringResource(R.string.common_cancel), color = TextMuted)
                    }
                },
            )
        }
        Text(stringResource(R.string.common_wellness_footer),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
    }
}
