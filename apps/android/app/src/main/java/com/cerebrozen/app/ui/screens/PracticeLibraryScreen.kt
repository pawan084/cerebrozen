package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.LineStroke
import kotlinx.coroutines.launch
import com.cerebrozen.app.ui.theme.Warm
import com.cerebrozen.app.ui.theme.WarmSoft
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.audio.Chime
import com.cerebrozen.app.ui.theme.Accent2
import com.cerebrozen.app.ui.theme.DangerSoft
import com.cerebrozen.app.ui.theme.FieldFill
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.OkSoft
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft


/** The first level under Explore's Calm now card: broad practice families,
 * before the user commits to a particular exercise. */
@Composable
fun PracticeLibraryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(CardFill, Night)),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().height(76.dp).background(CardFill.copy(alpha = .94f))
                .border(.5.dp, LineStroke.copy(alpha = .65f)).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            CircleAction(FieldFill, onBack) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFF6E376B), modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    stringResource(R.string.practicelib_title), maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = serif, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp,
                    ),
                    color = TextPrimary,
                )
                Text(
                    stringResource(R.string.practicelib_subtitle), maxLines = 1,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
                    color = TextMuted,
                )
            }
            CircleAction(DangerSoft, { onOpen("crisis") }) {
                Icon(Icons.Outlined.HealthAndSafety, "Urgent support", tint = Color(0xFFE34B4B), modifier = Modifier.size(23.dp))
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp).padding(top = 15.dp, bottom = 20.dp),
        ) {
            Text(
                stringResource(R.string.practicelib_eyebrow),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp),
                color = Color(0xFFB13D57),
            )
            Text(
                stringResource(R.string.practicelib_hero),
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 43.sp, lineHeight = 39.sp,
                ),
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.practicelib_intro),
                modifier = Modifier.padding(top = 14.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
                color = TextMuted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                PracticeFamilyRow(Icons.Outlined.Spa, stringResource(R.string.practicelib_breathe_title), stringResource(R.string.practicelib_breathe_sub), OkSoft, Color(0xFF4B775E)) { onOpen("breathing-intro") }
                // LocalFlorist, not HealthAndSafety: the red-cross shield is this
                // app's crisis symbol (Urgent support wears it on every screen),
                // and audit K found it labelling a routine practice family here.
                PracticeFamilyRow(Icons.Outlined.LocalFlorist, stringResource(R.string.practicelib_ground_title), stringResource(R.string.practicelib_ground_sub), WarmSoft, Color(0xFFC75270)) { onOpen("groundingintro") }
                PracticeFamilyRow(Icons.Outlined.FavoriteBorder, stringResource(R.string.practicelib_reset_title), stringResource(R.string.practicelib_reset_sub), WarmSoft, Color(0xFFC75270)) { onOpen("tipp") }
                PracticeFamilyRow(Icons.Outlined.Psychology, stringResource(R.string.practicelib_thoughts_title), stringResource(R.string.practicelib_thoughts_sub), WarmSoft, Color(0xFFC75270)) { onOpen("cbt") }
                PracticeFamilyRow(Icons.Outlined.Bedtime, stringResource(R.string.practicelib_sleep_title), stringResource(R.string.practicelib_sleep_sub), OkSoft, Color(0xFF4B775E)) { onOpen("bodyscan") }
                // The door `guidedimagery` never had. Four finished journeys —
                // forest, ocean, mountain, meadow, five steps each with voice
                // cues — were registered in the graph with nothing anywhere in
                // the app navigating to them. The sleep row above used to
                // advertise "Body scan and imagery" and open only the body
                // scan; imagery is its own family now, and that subtitle no
                // longer promises what it does not open.
                PracticeFamilyRow(Icons.Outlined.Landscape, stringResource(R.string.practicelib_imagery_title), stringResource(R.string.practicelib_imagery_sub), OkSoft, Color(0xFF4B775E)) { onOpen("guidedimagery") }
                PracticeFamilyRow(Icons.Outlined.AutoAwesome, stringResource(R.string.practicelib_gratitude_title), stringResource(R.string.practicelib_gratitude_sub), WarmSoft, Color(0xFFC75270)) { onOpen("gratitude") }
            }
        }
    }
}

@Composable
fun PracticeBreathingScreen(onBack: () -> Unit, onUrgent: () -> Unit, onBegin: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    // These were three `remember` booleans that wrote nowhere: flipping them
    // changed this screen and nothing else, and the session that follows read
    // its own settings — so the prep screen and the breath it prepares
    // disagreed. Chime and haptics are real, persisted settings
    // (audio/Chime.kt), and are now read from and written to there, which is
    // also what Breathe.kt's in-session toggle uses.
    //
    // "Keep screen awake" is gone rather than wired: nothing applies
    // FLAG_KEEP_SCREEN_ON for this session, so the switch could only have
    // promised something no code delivers.
    var chime by remember { mutableStateOf(Chime.breatheChimeEnabled) }
    var haptics by remember { mutableStateOf(Chime.breatheHapticsEnabled) }
    Column(Modifier.fillMaxSize().background(Night)) {
        Row(
            Modifier.fillMaxWidth().height(76.dp).background(CardFill.copy(alpha = .94f))
                .border(.5.dp, LineStroke.copy(alpha = .65f)).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            CircleAction(FieldFill, onBack) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFF6E376B), modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Breathing", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = serif), color = TextPrimary)
                Text("Prepare session", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            CircleAction(DangerSoft, onUrgent) {
                Icon(Icons.Outlined.HealthAndSafety, "Urgent support", tint = Color(0xFFE34B4B), modifier = Modifier.size(23.dp))
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 15.dp),
        ) {
            Text("BREATHING", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp), color = Color(0xFFB13D57))
            Text(
                "In for four,\nout for six.", modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp),
                color = TextPrimary,
            )
            Text(
                "A longer exhale may help settle physical activation. Stop if you feel dizzy or uncomfortable.",
                modifier = Modifier.padding(top = 13.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = TextMuted,
            )
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(FieldFill).padding(horizontal = 18.dp),
            ) {
                // These four strings already existed in strings.xml (breathprep_*)
                // and Hindi — the screen was rendering English literals past them.
                BreathingSetting(
                    stringResource(R.string.breathprep_chime_title),
                    stringResource(R.string.breathprep_chime_sub),
                    chime,
                ) {
                    chime = it; Chime.breatheChimeEnabled = it
                }
                BreathingSetting(
                    stringResource(R.string.breathprep_haptics_title),
                    stringResource(R.string.breathprep_haptics_sub),
                    haptics,
                    showDivider = false,
                ) {
                    haptics = it; Chime.breatheHapticsEnabled = it
                }
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 11.dp).height(49.dp).clip(RoundedCornerShape(25.dp))
                    .background(Accent2).clickable(onClick = onBegin),
                contentAlignment = Alignment.Center,
            ) {
                Text("Begin 2-minute breathing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun GratitudeReflectionScreen(onBack: () -> Unit, onUrgent: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    var reflection by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Night)) {
        PracticeHeader("Gratitude", "Positive reflection", serif, onBack, onUrgent)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 15.dp),
        ) {
            Text(
                "GRATITUDE REFLECTION",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp),
                color = Color(0xFFB13D57),
            )
            Text(
                "Notice one\nthing—not\neverything.",
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp,
                ),
                color = TextPrimary,
            )
            Text(
                "This is not about forcing positivity. A small neutral or supportive detail is enough.",
                modifier = Modifier.padding(top = 13.dp, bottom = 17.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                color = TextMuted,
            )
            OutlinedTextField(
                value = reflection,
                onValueChange = { reflection = it; status = null },
                placeholder = { Text("One thing that made today slightly easier...", color = Color(0xFF817980)) },
                modifier = Modifier.fillMaxWidth().height(78.dp),
                shape = RoundedCornerShape(18.dp),
                minLines = 2,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardFill, unfocusedContainerColor = CardFill,
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                ),
            )
            // The reflection is a real journal entry, not a local flag: this
            // button used to set `saved = true` and relabel itself "Saved
            // privately" while the text was never written anywhere, so the
            // promise was false and the writing was lost on navigation.
            val entryTitle = stringResource(R.string.practice_gratitude_entry_title)
            val savedStatus = stringResource(R.string.journal_saved)
            val queuedStatus = stringResource(R.string.practice_gratitude_queued)
            val saveFailed = stringResource(R.string.common_save_failed)
            val enabled = !busy && reflection.isNotBlank()
            Box(
                Modifier.fillMaxWidth().padding(top = 17.dp).height(49.dp).clip(RoundedCornerShape(25.dp))
                    .background(if (enabled) Accent2 else Color(0xFFB89AB2))
                    .clickable(enabled = enabled) {
                        busy = true; status = null
                        scope.launch {
                            try {
                                // Null = queued by the outbox with no connection.
                                // Saying "saved" flatly would re-tell the old lie
                                // in a quieter way, so the two outcomes read
                                // differently.
                                val entry = Api.createJournal(entryTitle, reflection.trim())
                                status = if (entry != null) savedStatus else queuedStatus
                                reflection = ""
                            } catch (e: Exception) {
                                status = e.message ?: saveFailed
                            } finally {
                                busy = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (busy) stringResource(R.string.common_one_moment)
                    else stringResource(R.string.practice_gratitude_save),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
            status?.let {
                Text(
                    it, modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
            }
            Text(
                stringResource(R.string.practice_gratitude_skip),
                modifier = Modifier.padding(start = 7.dp, top = 20.dp).clickable(onClick = onBack),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Periwinkle,
            )
        }
    }
}

/**
 * The regions whose crisis numbers someone here has actually checked against a
 * named official source.
 *
 * India's are checked against the MoHFW Tele-MANAS listing and the ERSS 112
 * listing — the same two sources the web `/safety` page cites. No other region
 * has been through that, so no other region gets to say "Verified".
 *
 * Deliberately a set and not `region == "IN"` inline in the composable: a badge
 * is a claim, and adding a region to it should be an edit someone makes on
 * purpose, next to this comment, with a source in hand.
 */
val VERIFIED_CRISIS_REGIONS = setOf("IN")

/** Whether the crisis screen may show the "Verified" badge for [region]. */
fun crisisRegionIsVerified(region: String): Boolean =
    region.trim().uppercase() in VERIFIED_CRISIS_REGIONS

@Composable
fun UrgentSupportScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    /** V2-d: true when composed from the ONBOARDING funnel, which has no
     * NavHost — the in-app doors (trusted contact, grounding, region, safety
     * plan) would be dead taps there, and a dead control on a crisis surface
     * is the one place that can never happen. The dial actions are intents
     * and work everywhere. This param is what let the duplicate
     * `CrisisScreen` be deleted (one crisis surface, one string set). */
    inFunnel: Boolean = false,
) {
    val serif = FontFamily(Font(R.font.newsreader))
    val region by rememberCrisisRegion()
    val regional = crisisLinesFor(region)
    val emergency = regional.firstOrNull { it.target in setOf("112", "911", "999", "000", "111") } ?: regional.first()
    val mental = primaryCrisisLine(region)
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Night)) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .96f)).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(FieldFill).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFFA52F50), modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.crisis_screen_title), style = MaterialTheme.typography.titleLarge.copy(fontFamily = serif), color = TextPrimary)
                Text(stringResource(R.string.crisis_screen_subtitle), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            // The banner is the immediate-danger answer, so it dials (audit
            // I#20): it told someone in immediate danger to "call 112 now" and
            // was not itself tappable — the instruction and the action were on
            // different parts of the screen.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DangerSoft)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { openSupportTarget(context, emergency.target) }
                    .padding(18.dp),
            ) {
                Text(stringResource(R.string.crisis_immediate_danger, emergency.target), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(stringResource(R.string.crisis_not_emergency), style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp), color = Color(0xFF542D34))
            }
            Spacer(Modifier.height(6.dp))
            // A badge is a CLAIM. This one used to render unconditionally, so
            // every region wore "Verified" — the exact bug the ref/ audit
            // found (Indian numbers badged Verified for every country). India
            // is the region whose numbers we have actually checked against a
            // named source; everywhere else says so plainly rather than
            // borrowing India's badge.
            val verifiedRegion = crisisRegionIsVerified(region)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.crisis_region_language, stringResource(regionLabelRes(region)).uppercase()), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = .8.sp), color = Color(0xFFB13D57))
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (verifiedRegion) OkSoft else WarmSoft)
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    Text(
                        stringResource(if (verifiedRegion) R.string.crisis_verified else R.string.crisis_not_verified),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = if (verifiedRegion) Ok else Warm,
                    )
                }
            }
            if (!verifiedRegion) {
                Text(
                    stringResource(R.string.crisis_unverified_note),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = TextMuted,
                )
            }
            Text(stringResource(R.string.crisis_headline), style = MaterialTheme.typography.displayLarge.copy(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp), color = TextPrimary)
            // "Reach VERIFIED human support" was a third copy of the same
            // unearned claim — true in India, not everywhere the screen renders.
            Text(
                stringResource(if (verifiedRegion) R.string.crisis_reach_verified else R.string.crisis_reach_plain),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = TextMuted,
            )
            // Tele-MANAS LEADS (audit I#20). This stack had emergency services
            // first as the only red-filled card, with the helpline beneath it —
            // while the documented contract (REDESIGN §2.3, the ordering
            // check-crisis-lines.mjs asserts across three stacks) is that the
            // mental-health line leads every crisis surface. The gate could not
            // see this screen because it reads the *directory*, not a layout.
            // The immediate-danger case is not demoted: the banner above answers
            // it before any card, and is now dialable itself.
            // Outside India this said "· verified mental-health support" — the
            // same unearned claim as the badge, on the line carrying the number
            // someone would actually dial.
            UrgentAction(
                stringResource(R.string.crisis_call_line, stringResource(mental.nameRes)),
                stringResource(
                    if (verifiedRegion) R.string.crisis_mental_india else R.string.crisis_mental_other,
                    mental.target,
                ),
                Icons.Outlined.FavoriteBorder,
                primary = true,
            ) { openSupportTarget(context, mental.target) }
            UrgentAction(stringResource(R.string.crisis_call_emergency), stringResource(R.string.crisis_emergency_detail, emergency.target), Icons.Outlined.Call) { openSupportTarget(context, emergency.target) }
            // A door with an honest label (audit I#21/#22). A guest cannot have
            // a trusted person, yet this card read "Contact my trusted person"
            // identically to the two that work — on a crisis surface, a door
            // that opens onto a setup form must say it is one. And it no longer
            // borrows Tele-MANAS's heart: a public helpline and a named
            // individual are different kinds of help, and here that matters.
            if (!inFunnel) {
                val hasAccount = Session.signedIn
                UrgentAction(
                    stringResource(if (hasAccount) R.string.crisis_trusted_title else R.string.crisis_trusted_setup_title),
                    stringResource(if (hasAccount) R.string.crisis_trusted_detail else R.string.crisis_trusted_setup_detail),
                    Icons.Outlined.PersonAddAlt,
                ) { onOpen("trustedcontact") }
                UrgentAction(stringResource(R.string.crisis_cannot_call), stringResource(R.string.crisis_cannot_call_detail), Icons.Outlined.Spa, sage = true) { onOpen("crisisgrounding") }
            }
            androidx.compose.material3.HorizontalDivider(color = LineStroke.copy(alpha = .7f))
            Text(stringResource(R.string.crisis_cached_title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextMuted)
            Text(stringResource(R.string.crisis_sources), style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = Color(0xFF776E6E))
            if (!inFunnel) {
                Text(stringResource(R.string.crisis_change_region), modifier = Modifier.padding(7.dp).clickable { onOpen("crisisregion") }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF6C2768))
                Box(
                    Modifier.fillMaxWidth().height(49.dp).clip(RoundedCornerShape(25.dp)).background(CardFill)
                        .border(1.dp, LineStroke, RoundedCornerShape(25.dp)).clickable { onOpen("safetyplan") },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.crisis_open_safety_plan), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF6C2768)) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun UrgentAction(title: String, detail: String, icon: ImageVector, primary: Boolean = false, sage: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (primary) 86.dp else 94.dp)
            .shadow(7.dp, shape, ambientColor = Color.Black.copy(alpha = .06f)).clip(shape)
            .background(if (primary) Color(0xFFD93B36) else CardFill)
            .then(if (primary) Modifier else Modifier.border(.5.dp, LineStroke.copy(alpha = .7f), shape))
            .clickable(onClick = onClick).padding(horizontal = 17.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(43.dp).clip(CircleShape).background(if (primary) DangerSoft else if (sage) OkSoft else DangerSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (primary) Color(0xFFE34B4B) else if (sage) Color(0xFF4B775E) else Color(0xFFD45369), modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (primary) Color.White else TextPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = if (primary) Color.White.copy(alpha = .92f) else TextMuted)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, divider: Boolean = true) {
    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
private fun NoticeChoice(symbol: String, title: String, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().height(if (subtitle == null) 72.dp else 82.dp)
            .shadow(7.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = .06f))
            .clip(RoundedCornerShape(22.dp)).background(CardFill).padding(horizontal = 27.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(21.dp),
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = Color(0xFF6C2768))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = Color(0xFF955386))
    }
}

@Composable
private fun PracticeHeader(
    title: String,
    subtitle: String,
    serif: FontFamily,
    onBack: () -> Unit,
    onUrgent: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(76.dp).background(CardFill.copy(alpha = .94f))
            .border(.5.dp, LineStroke.copy(alpha = .65f)).padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        CircleAction(FieldFill, onBack) {
            Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFF6E376B), modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = serif), color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        CircleAction(DangerSoft, onUrgent) {
            Icon(Icons.Outlined.HealthAndSafety, "Urgent support", tint = Color(0xFFE34B4B), modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun BreathingSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(73.dp).then(if (showDivider) Modifier.border(0.dp, Color.Transparent) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        // Was a raw Material Switch with its own hardcoded track and thumb
        // colours, which put it outside the design system AND outside the
        // accessibility fix that gave every AppSwitch a name — a screen reader
        // reached these two as an unlabelled "switch, on". Being the only bare
        // Switch left in the app, it also slipped past SwitchLabelTest, which
        // only knew to look at AppSwitch call sites.
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, label = title)
    }
}

@Composable
private fun CircleAction(fill: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(47.dp).clip(CircleShape).background(fill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PracticeFamilyRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconFill: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(77.dp)
            .shadow(9.dp, RoundedCornerShape(23.dp), ambientColor = Color.Black.copy(alpha = .08f))
            .clip(RoundedCornerShape(23.dp)).background(CardFill)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(43.dp).clip(CircleShape).background(iconFill), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF776E6E), maxLines = 1)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = Color(0xFF955386))
    }
}
