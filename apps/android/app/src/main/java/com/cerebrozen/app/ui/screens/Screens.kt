package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.outlined.Translate
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var region by remember { mutableStateOf("") }
    // Live state for the safety rows — a settings row that hides its state
    // makes every check a round-trip.
    var trustedLine by remember { mutableStateOf<String?>(null) }
    var signOutAsked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            val me = Api.me()
            name = me.optString("name")
            companion = me.optString("companion")
            language = me.optString("language")
            region = me.optString("region")
        }
        runCatching {
            trustedLine = Api.trustedContact()?.let { tc ->
                val who = tc.optString("name").ifBlank { tc.optString("value") }
                who.takeIf { it.isNotBlank() }
            }
        }
    }

    PremiumPage(stringResource(R.string.you_eyebrow), stringResource(R.string.you_title), trailing = Icons.Outlined.Settings) {
        // The profile row was the page's one dead card — it now opens the
        // companion/language settings (the closest thing to a profile editor)
        // and wears the same initial-letter avatar Home's header uses.
        SectionCard(onClick = { onOpen("companion") }) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape)
                        .background(Periwinkle.copy(alpha = 0.16f))
                        .border(1.dp, Periwinkle.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.trim().firstOrNull()?.uppercase() ?: "·",
                        style = MaterialTheme.typography.titleMedium, color = Periwinkle,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextMuted2,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // The persistent Support door (REDESIGN §2.3): calm, visually distinct,
        // and always two taps from anywhere — never a scare button. The line it
        // names and the number the pill dials follow the user's crisis region
        // (CrisisDirectory mirrors backend crisis.py) — a GB user gets
        // Samaritans, not an India-only number.
        val supportRegion by rememberCrisisRegion()
        val supportLine = primaryCrisisLine(supportRegion)
        val supportIsUrl = isSupportUrl(supportLine.target)
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
                    Text(
                        if (supportIsUrl) stringResource(supportLine.nameRes)
                        else stringResource(R.string.you_support_line,
                            stringResource(supportLine.nameRes), supportLine.target),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
                // One tap fewer on the path that matters most: a direct dial
                // action (ACTION_DIAL — opens the dialler, never places the
                // call itself; a finder URL opens the browser). The card still
                // opens the full crisis screen.
                run {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val callCd =
                        if (supportIsUrl) stringResource(R.string.crisis_open_cd, stringResource(supportLine.nameRes))
                        else stringResource(R.string.you_support_call_cd,
                            stringResource(supportLine.nameRes), supportLine.target)
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .border(1.dp, Warm.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable { openSupportTarget(ctx, supportLine.target) }
                            .semantics { contentDescription = callCd }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (supportIsUrl) stringResource(R.string.you_support_open)
                            else stringResource(R.string.you_support_call),
                            style = MaterialTheme.typography.labelLarge, color = Warm)
                    }
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

        // Group headers: eighteen visually identical rows scrolled as one
        // undifferentiated column — the legal-critical rows looked exactly like
        // "Take a quick tour". Same header style the Legal section already used.
        Text(stringResource(R.string.you_group_personalize), style = MaterialTheme.typography.labelSmall,
            color = Periwinkle, modifier = Modifier.padding(top = 8.dp))
        PremiumNavRow(stringResource(R.string.you_companion_title),
            stringResource(R.string.you_companion_subtitle, run {
                val comp = companion.ifBlank { "Calm Guide" }
                companionLabelRes(comp)?.let { stringResource(it) } ?: comp
            }),
            icon = Icons.Outlined.ChatBubbleOutline, emphasis = true) { onOpen("companion") }
        // Onboarding asked for a language and nothing could change the answer:
        // the profile card above SHOWED it ("Calm Guide · Hindi") but opened the
        // companion picker, so a mistyped first run was permanent.
        PremiumNavRow(stringResource(R.string.you_language_title),
            stringResource(R.string.you_language_subtitle, run {
                val lang = language.ifBlank { "English" }
                languageLabelRes(lang)?.let { stringResource(it) } ?: lang
            }),
            icon = Icons.Outlined.Translate) { onOpen("language") }
        // Rows carry their current value — a settings row that hides its state
        // makes every check a round-trip.
        PremiumNavRow(
            stringResource(R.string.you_appearance_title),
            stringResource(
                R.string.you_appearance_state,
                when (com.cerebrozen.app.ui.theme.AppTheme.mode) {
                    com.cerebrozen.app.ui.theme.ThemeMode.Night -> stringResource(R.string.theme_night_title)
                    com.cerebrozen.app.ui.theme.ThemeMode.Dawn -> stringResource(R.string.theme_dawn_title)
                    else -> stringResource(R.string.theme_system_title)
                },
            ),
            icon = Icons.Outlined.DarkMode,
        ) { onOpen("appearance") }
        run {
            // Read fresh each composition so returning from the sub-screen
            // shows the new state (a remember{} here would go stale).
            val reminderOn = androidx.compose.ui.platform.LocalContext.current
                .getSharedPreferences("cerebro", android.content.Context.MODE_PRIVATE)
                .getBoolean("reminder_on", false)
            PremiumNavRow(
                stringResource(R.string.you_reminder_title),
                if (reminderOn) stringResource(R.string.you_reminder_state_on)
                else stringResource(R.string.you_reminder_state_off),
                icon = Icons.Outlined.NotificationsNone,
            ) { onOpen("reminders") }
        }
        Text(stringResource(R.string.you_group_progress), style = MaterialTheme.typography.labelSmall,
            color = Periwinkle, modifier = Modifier.padding(top = 8.dp))
        // The one screen the user fills in rather than reads.
        PremiumNavRow(stringResource(R.string.you_goals_title), stringResource(R.string.you_goals_subtitle),
            icon = Icons.Outlined.Flag) { onOpen("goals") }
        PremiumNavRow(stringResource(R.string.you_insights_title), stringResource(R.string.you_insights_subtitle),
            icon = Icons.Outlined.Insights) { onOpen("insights") }
        // Trends sits beside Insights, not inside it: Insights is what the app
        // has to say, Trends is what the user's own entries look like. They
        // answer different questions and one should not be buried in the other.
        PremiumNavRow(stringResource(R.string.you_trends_title), stringResource(R.string.you_trends_subtitle),
            icon = Icons.Outlined.ShowChart) { onOpen("trends") }
        PremiumNavRow(stringResource(R.string.you_patterns_title), stringResource(R.string.you_patterns_subtitle),
            icon = Icons.Outlined.Psychology) { onOpen("patterns") }

        Text(stringResource(R.string.you_group_safety), style = MaterialTheme.typography.labelSmall,
            color = Periwinkle, modifier = Modifier.padding(top = 8.dp))
        PremiumNavRow(stringResource(R.string.you_privacy_title), stringResource(R.string.privacy_control_line),
            icon = Icons.Outlined.Lock) { onOpen("privacy") }
        PremiumNavRow(stringResource(R.string.you_safetyplan_title), stringResource(R.string.you_safetyplan_subtitle),
            icon = Icons.Outlined.Shield) { onOpen("safetyplan") }
        // The Crisis screen's "add one in Settings" now has a Settings to mean.
        // Both safety rows carry their live state — "one person, only if things
        // get hard" told you nothing about whether that person exists yet.
        PremiumNavRow(
            stringResource(R.string.trusted_title),
            trustedLine?.let { stringResource(R.string.you_trusted_saved, it) }
                ?: stringResource(R.string.you_trusted_subtitle),
            icon = Icons.Outlined.PersonAddAlt,
        ) { onOpen("trustedcontact") }
        PremiumNavRow(
            stringResource(R.string.you_crisisregion_title),
            if (region.isNotBlank()) stringResource(R.string.you_crisisregion_state, region.uppercase())
            else stringResource(R.string.you_crisisregion_subtitle),
            icon = Icons.Outlined.Public,
        ) { onOpen("crisisregion") }
        PremiumNavRow(stringResource(R.string.humansupport_title), stringResource(R.string.you_humansupport_subtitle),
            icon = Icons.Outlined.Diversity3) { onOpen("humansupport") }

        // The one upsell surface carries an occasional sheen (iOS parity) — the
        // rest of You stays still, which is what makes this row read as the
        // offer rather than as another setting.
        Box(Modifier.sheen().padding(top = 8.dp)) {
            PremiumNavRow(stringResource(R.string.you_premium_title), stringResource(R.string.you_premium_subtitle),
                icon = Icons.Outlined.WorkspacePremium) { onOpen("premium") }
        }

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

        // Replay-the-tour is a once-ever action: it lives at the bottom now,
        // not above the safety rows it used to outrank.
        PremiumNavRow(stringResource(R.string.you_tour_title), stringResource(R.string.you_tour_subtitle),
            icon = Icons.Outlined.Explore) { TourState.reset(); onOpen("home") }

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
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp))
        // The quiet fact every support conversation starts with.
        Text(
            stringResource(R.string.you_version, com.cerebrozen.app.BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall, color = TextMuted2,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
    }
}
