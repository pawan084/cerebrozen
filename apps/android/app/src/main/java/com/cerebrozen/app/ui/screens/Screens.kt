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
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WorkOutline
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
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
 * routing to sub-screens, then legal/account actions and sign out.
 *
 * V3-c: it takes an [onBack] because it stopped being a tab. The nav rule that
 * drops the tab pill here ("a pill under a settings room would light nothing")
 * only holds if the room carries its own way out — and on glass this screen had
 * neither: no pill, no back arrow, a gear in the corner that had already been
 * used to get in. Gesture-back worked; nothing on screen said so.
 */
@Composable
fun YouScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var companion by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    // Live state for the safety rows — a settings row that hides its state
    // makes every check a round-trip.
    var trustedLine by remember { mutableStateOf<String?>(null) }
    var signOutAsked by remember { mutableStateOf(false) }
    // Same three-state entitlement the Premium screen branches on, so the row
    // that opens it does not promise an upsell the screen will not deliver.
    var tier by remember { mutableStateOf(Session.cachedTier()) }
    var sponsored by remember { mutableStateOf(Session.cachedSponsored()) }

    LaunchedEffect(Session.signedIn) {
        if (!Session.signedIn) return@LaunchedEffect
        runCatching {
            val me = Api.me()
            name = me.optString("name")
            companion = me.optString("companion")
            language = me.optString("language")
            region = me.optString("region")
            tier = me.optString("subscription_tier").ifBlank { "free" }
            sponsored = me.optBoolean("sponsored")
            Session.rememberEntitlement(tier, sponsored)
        }
        runCatching {
            trustedLine = Api.trustedContact()?.let { tc ->
                val who = tc.optString("name").ifBlank { tc.optString("value") }
                who.takeIf { it.isNotBlank() }
            }
        }
        // The month presence grid left this screen with the V3-c density pass —
        // and its mood fetch left with it, rather than costing every open of
        // Settings a round-trip nothing draws. `presenceMonth` stays (pure,
        // tested) for whichever surface earns the grid next.
    }

    PremiumSubPage(stringResource(R.string.you_eyebrow), stringResource(R.string.you_title), onBack = onBack) {
        // The profile row was the page's one dead card — it now opens the
        // companion/language settings (the closest thing to a profile editor)
        // and wears the same initial-letter avatar Home's header uses.
        // In guest mode there is no profile to edit and no name to show: the
        // fallback rendered a row titled "You" (the screen's own name) over an
        // empty avatar — a placeholder that shipped (audit I#17). A guest's
        // profile row IS the sign-in door, and says so.
        val isGuest = Session.guestMode && !Session.signedIn
        SectionCard(onClick = { onOpen(if (isGuest) "auth" else "companion") }) {
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
                    Text(
                        if (isGuest) stringResource(R.string.you_guest_name)
                        else name.ifBlank { stringResource(R.string.you_default_name) },
                        style = MaterialTheme.typography.titleMedium, color = TextSoft,
                    )
                    Text(
                        if (isGuest) stringResource(R.string.you_guest_sub)
                        // Known taxonomy values localize for DISPLAY; the
                        // server-side values stay English (contract).
                        else run {
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
                    val supportName = stringResource(supportLine.nameRes)
                    Text(
                        // "988 Suicide & Crisis Lifeline · 988" said the number
                        // twice (audit K): append the target only when the name
                        // doesn't already carry it.
                        if (supportIsUrl || supportName.contains(supportLine.target)) supportName
                        else stringResource(R.string.you_support_line, supportName, supportLine.target),
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
                    // 48dp floor + Role.Button (audit I#18). This pill sat at
                    // ~33px on a 320dpi handset — the smallest target on the
                    // screen was the one that opens the dialler on a crisis
                    // line, two finger-widths from a chevron that merely
                    // navigates. The consequence of a mis-tap is contained
                    // (ACTION_DIAL pre-fills, it never places the call), but a
                    // control this consequential does not get to be the one
                    // below the floor. The visual pill keeps its size; the
                    // TARGET grows around it.
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button) { openSupportTarget(ctx, supportLine.target) }
                            .semantics { contentDescription = callCd },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (supportIsUrl) stringResource(R.string.you_support_open)
                            else stringResource(R.string.you_support_call),
                            style = MaterialTheme.typography.labelLarge, color = Warm,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, Warm.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
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

        // Work coaching — only when an organisation sponsors this account.
        // Session.cachedSponsored() mirrors the server's entitlement resolve;
        // the backend still enforces the gate (403) whatever this cache says,
        // so the row can never unlock anything by itself.
        if (Session.cachedSponsored()) {
            PremiumNavRow(
                stringResource(R.string.you_work_row_title),
                stringResource(R.string.you_work_row_sub),
                icon = Icons.Outlined.WorkOutline,
            ) { onOpen("work") }
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
            // No `emphasis` (audit I#19): the flag tints the icon well Cyan,
            // which at 11% alpha on the light card read as grey-blue — the one
            // well on the page that looked unstyled rather than emphasised.
            icon = Icons.Outlined.ChatBubbleOutline) { onOpen("companion") }
        // V2-e part 2: ONE row for appearance + language (REDESIGN_V2 §3.7) —
        // the Appearance screen carries the language door. The subtitle keeps
        // both current values, so neither setting hides its state.
        PremiumNavRow(
            stringResource(R.string.you_appearance_lang_title),
            run {
                val theme = when (com.cerebrozen.app.ui.theme.AppTheme.mode) {
                    com.cerebrozen.app.ui.theme.ThemeMode.Night -> stringResource(R.string.theme_night_title)
                    com.cerebrozen.app.ui.theme.ThemeMode.Dawn -> stringResource(R.string.theme_dawn_title)
                    else -> stringResource(R.string.theme_system_title)
                }
                val lang = language.ifBlank { "English" }
                val langLabel = languageLabelRes(lang)?.let { stringResource(it) } ?: lang
                "$theme · $langLabel"
            },
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
            // The reference pairs the reminder settings with their history
            // (YOU-04 → TOD-06): "is it on" and "did it fire" are different
            // questions, and only the second one has evidence behind it.
            PremiumNavRow(
                stringResource(R.string.inbox_title),
                stringResource(R.string.inbox_eyebrow),
                icon = Icons.Outlined.Inbox,
            ) { onOpen("notifications") }
        }
        Text(stringResource(R.string.you_group_progress), style = MaterialTheme.typography.labelSmall,
            color = Periwinkle, modifier = Modifier.padding(top = 8.dp))
        // The one screen the user fills in rather than reads.
        PremiumNavRow(stringResource(R.string.you_goals_title), stringResource(R.string.you_goals_subtitle),
            icon = Icons.Outlined.Flag) { onOpen("goals") }
        // V2-d: ONE analytics door. Trends and Patterns rows left this list —
        // the Insights screen already doors both (its pills and rows), so the
        // three rows here were three names for one place, which is exactly the
        // five-similarly-named-surfaces confusion Audit L measured.
        PremiumNavRow(stringResource(R.string.you_insights_title), stringResource(R.string.you_insights_subtitle),
            icon = Icons.Outlined.Insights) { onOpen("insights") }

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
        // V2-d: the crisis-region row left — the region picker lives IN
        // context on the crisis screen ("Showing IN · change"), which is the
        // moment a wrong-country list actually matters.
        PremiumNavRow(stringResource(R.string.humansupport_title), stringResource(R.string.you_humansupport_subtitle),
            icon = Icons.Outlined.Diversity3) { onOpen("humansupport") }

        // The one upsell surface carries an occasional sheen (iOS parity) — the
        // rest of You stays still, which is what makes this row read as the
        // offer rather than as another setting. Which is precisely why it must
        // stop moving once there is nothing to offer: a member who already has
        // premium, sponsored or bought, gets the plain row and a subtitle that
        // says what the screen behind it will actually say.
        val premiumSubtitle = when {
            sponsored -> stringResource(R.string.you_premium_subtitle_sponsored)
            tier != "free" -> stringResource(R.string.you_premium_subtitle_active)
            else -> stringResource(R.string.you_premium_subtitle)
        }
        // V2-d honesty: on Android nothing is purchasable (Play Billing is not
        // built), so a free, unsponsored member gets NO premium row — a door to
        // a screen whose only content is "billing isn't wired" is an upsell to
        // nothing. The row returns with Play Billing; members who already have
        // premium (sponsored or bought elsewhere) keep their manage door.
        // WC-10: the row returns with Play Billing, exactly as the note above
        // said it would — and on the same condition that removed it. A free
        // member sees it only when Play has something purchasable to offer, so
        // an unconfigured build still shows no door to an upsell it cannot
        // honour.
        if (sponsored || tier != "free" || com.cerebrozen.app.net.BillingBridge.purchasable) {
            Box(Modifier.padding(top = 8.dp)) {
                PremiumNavRow(stringResource(R.string.you_premium_title), premiumSubtitle,
                    icon = Icons.Outlined.WorkspacePremium) { onOpen("premium") }
            }
        }

        // V2-e part 2: the legal trio (policy · export · delete) lives inside
        // Privacy & memory now — with the controls it documents, and with the
        // delete row keeping its danger tint there.

        // V2-d: the replay-the-tour row left with the tour itself — Today's
        // 4-stop modal became a one-line hint (V2-b), and a row to re-arm a
        // hint is not worth a slot on this list.

        // Sign out was a bare TextButton in TextMuted — a caption, on a screen
        // where every other action is a bordered card — and it signed you out on
        // the first tap. Session.signOut() clears the local store, so an
        // accidental tap took the cached reads and any unsaved draft with it.
        // Now a row that looks like a control, and one question first.
        NavRow(
            if (Session.guestMode) stringResource(R.string.guest_sign_in_action)
            else stringResource(R.string.you_signout),
            if (Session.guestMode) stringResource(R.string.guest_sign_in_subtitle)
            else stringResource(R.string.you_signout_subtitle),
            icon = Icons.AutoMirrored.Outlined.Logout,
        ) {
            if (Session.guestMode) onOpen("auth") else signOutAsked = true
        }
        if (signOutAsked && !Session.guestMode) {
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
