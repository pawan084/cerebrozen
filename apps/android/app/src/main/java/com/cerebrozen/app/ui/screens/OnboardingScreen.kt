package com.cerebrozen.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.cerebrozen.app.ui.theme.CardFill
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Analytics
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.net.funnelStepName
import com.cerebrozen.app.ui.BrandMark
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.ChipSelectedFill
import com.cerebrozen.app.ui.theme.ChipSelectedInk
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import com.cerebrozen.app.ui.theme.ChipFill
import com.cerebrozen.app.ui.theme.EyebrowMuted
import com.cerebrozen.app.ui.theme.GratitudeCardFill
import com.cerebrozen.app.ui.theme.GratitudeAvatarFill
import com.cerebrozen.app.ui.theme.GratitudeCaption
import com.cerebrozen.app.ui.theme.InfoCardFill
import com.cerebrozen.app.ui.theme.InfoCardStroke
import com.cerebrozen.app.ui.theme.InfoCardHint
import com.cerebrozen.app.ui.theme.InfoCardDivider
import com.cerebrozen.app.ui.theme.WelcomeGradientTop
import com.cerebrozen.app.ui.theme.WelcomeGradientBottom
import com.cerebrozen.app.ui.theme.WelcomeTitleText
import com.cerebrozen.app.ui.theme.WelcomeSubtitleText
import com.cerebrozen.app.ui.theme.WelcomeSecondaryText
import com.cerebrozen.app.ui.theme.WelcomeOrbMid
import com.cerebrozen.app.ui.theme.WelcomeOrbEdge
import com.cerebrozen.app.ui.theme.WelcomeOrbHaloInner
import com.cerebrozen.app.ui.theme.WelcomeOrbHaloOuter
import com.cerebrozen.app.ui.theme.WelcomeOrbHaloDisc
import com.cerebrozen.app.ui.theme.PrimaryButtonFill
import com.cerebrozen.app.ui.theme.PrimaryButtonInk
import com.cerebrozen.app.ui.theme.PrimaryButtonDisabledFill
import com.cerebrozen.app.ui.theme.ResetDoneFill
import com.cerebrozen.app.ui.theme.FunnelHeaderTop
import com.cerebrozen.app.ui.theme.FunnelHeaderBottom
import com.cerebrozen.app.ui.theme.FunnelBodyText
import com.cerebrozen.app.ui.theme.ProgressTrack
import com.cerebrozen.app.ui.theme.PickRowSelectedFill
import com.cerebrozen.app.ui.theme.PickRowFill
import com.cerebrozen.app.ui.theme.PickRowStroke
import com.cerebrozen.app.ui.theme.PickRowChevron
import com.cerebrozen.app.ui.theme.PickCardStroke
import com.cerebrozen.app.ui.theme.DotUnselectedFill
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.cerebrozen.app.ui.theme.FieldFill
import com.cerebrozen.app.ui.theme.Ok

internal enum class OStep {
    Welcome, Language, Intro, Disclosure, State, Reset, Reflection, Consent, Notify, Guest, SignUp, Ready, Under18
}

private val OnboardingSerif = FontFamily(Font(R.font.newsreader))

/** Total funnel steps, as the app bar counts them ("3 of 10"). The prototype's
 * ONB-xx numbering is the contract; [funnelStepIndex] maps this client's steps
 * onto it, so a step this app has not built yet leaves a gap in the count
 * rather than renumbering the ones around it. */
internal const val ONBOARDING_STEPS = 10

/**
 * Which of the prototype's ten onboarding steps this one is (1-based).
 *
 * Deliberately NOT `OStep.ordinal + 1`. The ref/mobile.html numbering is a
 * fixed contract shared with iOS and `apps/app` — ONB-01 is the language pick,
 * ONB-04 is the first check-in — and this client does not yet ship ONB-06
 * (post-reset reflection) or ONB-10 (the ready screen). Deriving from the enum
 * would silently renumber every later step the day one of those lands, and the
 * user-visible "N of 10" would disagree with the spec, the other clients and
 * the analytics funnel all at once.
 */
internal fun funnelStepIndex(step: OStep): Int = when (step) {
    OStep.Welcome -> 0        // SPL-01: the launch screen, outside the count
    OStep.Language -> 1       // ONB-01
    OStep.Intro -> 2          // ONB-02
    OStep.Disclosure -> 3     // ONB-03
    OStep.State -> 4          // ONB-04
    OStep.Reset -> 5          // ONB-05
    OStep.Reflection -> 6     // ONB-06
    OStep.Consent -> 7        // ONB-07
    OStep.Notify -> 8         // ONB-08
    OStep.Guest, OStep.SignUp -> 9 // ONB-09 / account branch
    OStep.Ready -> 10         // ONB-10
    OStep.Under18 -> 0        // ONB-11: branch, outside the ten-step count
}

/** How far along the funnel a step sits. Keyed off the step itself — never off
 * the eyebrow copy, which is translated (a Hindi device used to fall through to
 * 1f and snap the bar to full from the Language step on). Pure + unit-tested. */
internal fun funnelProgress(step: OStep): Float = when (step) {
    OStep.Welcome -> 0f
    OStep.Language -> 0.1f
    OStep.Intro -> 0.2f
    OStep.Disclosure -> 0.3f
    OStep.State -> 0.4f
    OStep.Reset -> 0.5f
    OStep.Reflection -> 0.6f
    OStep.Consent -> 0.75f
    OStep.Notify -> 0.88f
    OStep.Guest, OStep.SignUp -> 0.9f
    OStep.Ready -> 1f
    OStep.Under18 -> 0f
}

/** One feeling tap is the whole "assessment" — each maps into the shared
 * motivation/goal taxonomy (cross-stack: iOS StateCheckScreen.states ⇄ web
 * lib/onboarding.FEELINGS) so plans and conversation starters ground on it.
 * `mood` keys the first check-in into the shared mood taxonomy.
 *
 * [id] is our own stable key (saver / selection identity); [labelRes] is the
 * display copy and localizes freely. motivation/goal/mood are WIRE VALUES of the
 * cross-stack taxonomy — never translate them. */
/** The taxonomy note for a wire mood — what a check-in FROM THE FUNNEL stores
 * as its note. This used to be the literal string "From onboarding", which then
 * rendered on Home's Recent check-ins as provenance jargon nobody asked for.
 * WIRE VALUES (hand-duplicated with iOS/web) — never translated. */
internal fun onboardingMoodNote(mood: String): String = when (mood) {
    "Good" -> "Clear"
    "Anxious" -> "Loud thoughts"
    "Low" -> "Heavy"
    "Tired" -> "Need rest"
    "Not sure" -> "Closest fit right now"
    // Retained for rows written before the taxonomy converged on six states:
    // old check-ins still hold "Okay" and must still render a note.
    "Okay" -> "Neutral"
    else -> ""
}

internal data class StateOption(
    val id: String,
    @androidx.annotation.StringRes val labelRes: Int,
    val motivation: String,
    val goal: String,
    val mood: String,
)

internal val STATE_OPTIONS = listOf(
    StateOption("stressed", R.string.ob_state_opt_stressed, "Calm", "Reduce stress", "Anxious"),
    StateOption("night", R.string.ob_state_opt_night, "Calm", "Sleep better", "Tired"),
    StateOption("overthinking", R.string.ob_state_opt_overthinking, "Focus", "Stop overthinking", "Anxious"),
    StateOption("doubt", R.string.ob_state_opt_doubt, "Confidence", "Build confidence", "Low"),
    StateOption("distant", R.string.ob_state_opt_distant, "Connection", "Feel less alone", "Low"),
    // "Okay" was outside the six-state taxonomy every check-in screen offers
    // (TodayScreen.MOODS / backend services/moods.py), so this seeded a first
    // mood no picker could show back. "Not sure" is the honest in-taxonomy
    // value: this option names a GOAL, not a feeling, so the app genuinely does
    // not know how the person feels — and the server scores unknown as neither
    // distress nor contentment, which is the same neutral "Okay" got.
    StateOption("consistency", R.string.ob_state_opt_consistent, "Discipline", "Strengthen willpower", "Not sure"),
)

/** A pickable chip: a stable [id] the code branches on, plus localizable copy. */
internal data class PickOption(val id: String, @androidx.annotation.StringRes val labelRes: Int)

/** The app-language options. `internal` because You → Language re-offers exactly
 * this list: onboarding asked once and nothing could change the answer afterwards,
 * and a second hand-kept copy would have drifted from the wire values. */
internal val LANGUAGES = listOf(
    PickOption("English", R.string.ob_lang_english),
    PickOption("Hindi", R.string.ob_lang_hindi),
    PickOption("Hinglish", R.string.ob_lang_hinglish),
    PickOption("Punjabi", R.string.ob_lang_punjabi),
    PickOption("Tamil", R.string.ob_lang_tamil),
)

/** Display label for a persisted app-language value; unknown shows as stored.
 * (You → Settings renders the saved value, which is the English wire string.) */
internal fun languageLabelRes(value: String): Int? =
    LANGUAGES.firstOrNull { it.id == value }?.labelRes
/** When the daily reminder fires. Single-select, so every option here must be a
 * TIME — anything else silently means "none" once [applyReminderChoice] falls
 * through its `when`.
 *
 * A "Private previews" chip used to sit in this group. Nothing read the value: it
 * was never persisted, no preview setting exists anywhere in the app, and the
 * reminder it would have hidden says only "A moment for you". What it actually
 * did was turn reminders off — so a user who tapped it, wanting a *discreet*
 * daily nudge, got no nudge at all and was told nothing. */
internal val NOTIFY = listOf(
    PickOption("morning", R.string.ob_notify_morning),
    PickOption("evening", R.string.ob_notify_evening),
    PickOption("custom", R.string.ob_notify_custom),
    PickOption("none", R.string.ob_notify_none),
)

/** The hour a reminder option schedules, or null for "no reminder".
 *
 * Pure and internal so the invariant is a TEST rather than a comment: every id
 * in [NOTIFY] must resolve here. When it was an inline `when` with a silent
 * `else`, an option was added to the chip group that this mapping had never
 * heard of, and choosing it quietly meant "off". */
internal fun reminderHourFor(option: String): Int? = when (option) {
    "morning" -> 8
    "evening" -> 21
    "custom" -> 21
    "none" -> null
    else -> null
}
// Consent rows render from the localized notice (DPDP s.5(3) — ConsentNotice.kt).

/** Set when the post-sign-up consent write never reached the server, so Privacy
 * can say so instead of letting a failed write pass silently. */
internal const val CONSENT_SYNC_FAILED_KEY = "consent_sync_failed"

// Savers so a rotation / process death mid-funnel keeps the user's place and their
// selections instead of dropping them back to Welcome.
private val StateOptionSaver = Saver<StateOption?, String>(
    save = { it?.id ?: "" },
    restore = { id -> STATE_OPTIONS.firstOrNull { it.id == id } },
)
private val ConsentSaver = mapSaver(
    save = { it.toMap() },
    restore = { restored ->
        mutableStateMapOf<String, Boolean>().apply {
            restored.forEach { (k, v) -> put(k, v as Boolean) }
        }
    },
)

/** Whether the soft keyboard is showing.
 *
 * Every BackHandler in this funnel gates on it. Back's FIRST job on Android is
 * to dismiss the keyboard, and a handler that is unconditionally enabled eats
 * that press: on the sign-in step it navigated away instead, so a user closing
 * the keyboard was thrown back to Welcome with the email and password they had
 * just typed. Caught on a device the same evening the BackHandler was added. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun imeVisible(): Boolean = WindowInsets.isImeVisible

/**
 * Value-first onboarding funnel — the adult gate, honesty disclosure, a first
 * calming reset, then account + consent. New users flow through here; returning
 * users tap through to the existing [AuthScreen]. Consent/notification prefs are
 * collected locally and applied right after sign-up so the session flips once.
 */
@Composable
fun Onboarding() {
    var signIn by rememberSaveable { mutableStateOf(false) }
    var urgentSupport by rememberSaveable { mutableStateOf(false) }
    if (urgentSupport) {
        androidx.activity.compose.BackHandler { urgentSupport = false }
        CrisisScreen(onBack = { urgentSupport = false })
        return
    }
    if (signIn) {
        androidx.activity.compose.BackHandler(enabled = !imeVisible()) { signIn = false }
        AuthScreen(onBack = { signIn = false })
        return
    }

    var step by rememberSaveable { mutableStateOf(OStep.Welcome) }
    val order = listOf(
        OStep.Welcome, OStep.Language, OStep.Intro, OStep.Disclosure, OStep.State,
        OStep.Reset, OStep.Reflection, OStep.Consent, OStep.Notify, OStep.Guest, OStep.Ready,
    )
    fun next() { val i = order.indexOf(step); if (i < order.lastIndex) step = order[i + 1] }
    fun back() {
        when (step) {
            OStep.Under18 -> step = OStep.Disclosure
            OStep.SignUp -> step = OStep.Guest
            else -> { val i = order.indexOf(step); if (i > 0) step = order[i - 1] }
        }
    }

    // The system back gesture walks the funnel, exactly like the on-screen back.
    // Without this it fell through to the Activity and FINISHED it: found on a
    // device, a back swipe from any of the eight steps dropped the user onto the
    // launcher, and relaunching restarted at Welcome — language, feeling and
    // consent choices all gone, because rememberSaveable cannot survive an
    // activity that was destroyed rather than recreated. Back is the most-used
    // navigation control on Android; it was a trapdoor out of onboarding.
    // Disabled on Welcome so back there still leaves the app, as expected, and
    // while the keyboard is up so back still closes the keyboard first.
    androidx.activity.compose.BackHandler(enabled = step != OStep.Welcome && !imeVisible()) { back() }

    // First-party funnel counts (anonymous install id, opt-out; mirrors iOS).
    LaunchedEffect(step) { Analytics.track("onboarding_step", funnelStepName(step.name)) }

    var language by rememberSaveable { mutableStateOf("English") }
    var state by rememberSaveable(stateSaver = StateOptionSaver) { mutableStateOf<StateOption?>(null) }
    var notify by rememberSaveable { mutableStateOf("evening") }
    var ageConfirmed by rememberSaveable { mutableStateOf(false) }
    // Did they actually breathe, or press "Skip for now"? The Notify step used
    // to congratulate everyone on "your first win" either way — telling a user
    // who skipped that they had achieved something they had just declined.
    var resetDone by rememberSaveable { mutableStateOf(false) }
    // Private by default: NOTHING pre-ticked — consent must be an action
    // (EDPB/ICO; matches iOS ConsentScreen + web onboarding). The 38a63fa fix
    // was silently reverted by the cc7cbd4 "ui" commit (same commit as the
    // namespace slip) — restored 2026-07-25; keep every default false.
    val consent = rememberSaveable(saver = ConsentSaver) {
        mutableStateMapOf(
            "mood_history" to true, "ai_memory" to true, "journal_memory" to false,
            "sleep_history" to false, "voice_storage" to false, "model_training" to false,
        )
    }

    // Apply the onboarding reminder choice for real: persist it, schedule the daily
    // alarm at the chosen hour, and ask for notification permission (Android 13+).
    // Without this the Notify step's selection did nothing.
    val context = LocalContext.current
    val notifyPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun applyReminderChoice() {
        val prefs = context.getSharedPreferences("cerebro", Context.MODE_PRIVATE)
        val hour = reminderHourFor(notify)
            ?: run { prefs.edit().putBoolean("reminder_on", false).apply(); return }
        prefs.edit().putBoolean("reminder_on", true).apply()
        com.cerebrozen.app.notify.Reminders.schedule(context, hour)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // W10: calm step transitions — a 250ms fade with a slight directional slide
    // (forward slides in from the right, back from the left). Reduce Motion
    // snaps between steps instead (no transition, never blank).
    val reduceMotion = rememberReduceMotion()
    val slidePx = with(LocalDensity.current) { 24.dp.roundToPx() }
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val forward = targetState.ordinal >= initialState.ordinal
                (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { if (forward) slidePx else -slidePx })
                    .togetherWith(fadeOut(tween(250)))
            }
        },
        label = "onboarding-step",
    ) { current ->
        when (current) {
        OStep.Welcome -> Welcome(onStart = { next() }, onSignIn = { signIn = true })

        OStep.Intro -> Funnel(
            OStep.Intro,
            stringResource(R.string.ob_intro_eyebrow), stringResource(R.string.ob_intro_title),
            stringResource(R.string.ob_intro_sub), stringResource(R.string.ob_intro_cta),
            onBack = { back() }, onPrimary = { next() },
            barTitle = stringResource(R.string.ob_intro_bar_title),
            onUrgent = { urgentSupport = true },
        ) {
            Text(stringResource(R.string.ob_intro_section),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = OnboardingSerif), color = TextPrimary)
            OnboardingFeatureCard("♧", stringResource(R.string.ob_intro_settle), stringResource(R.string.ob_intro_settle_sub))
            OnboardingFeatureCard("□", stringResource(R.string.ob_intro_talk), stringResource(R.string.ob_intro_talk_sub))
            OnboardingFeatureCard("▣", stringResource(R.string.ob_intro_write), stringResource(R.string.ob_intro_write_sub))
        }

        OStep.Disclosure -> Funnel(
            OStep.Disclosure,
            stringResource(R.string.ob_disclosure_eyebrow), stringResource(R.string.ob_disclosure_title),
            stringResource(R.string.ob_disclosure_sub),
            stringResource(R.string.ob_disclosure_cta), onBack = { back() }, onPrimary = { next() },
            progress = 0.25f,
            barTitle = stringResource(R.string.ob_disclosure_eyebrow),
            primaryEnabled = ageConfirmed,
            onUrgent = { urgentSupport = true },
            secondary = {
                TextButton(onClick = { step = OStep.Under18 }) {
                    Text(stringResource(R.string.ob_under_18), color = Periwinkle, fontWeight = FontWeight.Bold)
                }
            },
        ) {
            ReferenceCard(borderColor = Danger.copy(alpha = .20f), fill = Danger.copy(alpha = .06f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Danger.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = Danger, modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ob_immediate_danger), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(stringResource(R.string.ob_immediate_danger_sub), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                SecondaryOnboardingButton(stringResource(R.string.ob_urgent_support)) { urgentSupport = true }
            }
            // The age gate STATES the requirement. It used to show a tick with
            // "Confirmed: I am 18 or older / Thank you" the instant the step
            // opened — a compliance surface asserting a confirmation the user had
            // not made, and thanking them for it. The confirmation is the CTA.
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.ob_age_confirm), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(stringResource(R.string.ob_age_confirm_sub), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                AppSwitch(checked = ageConfirmed, onCheckedChange = { ageConfirmed = it })
            }
            // Two-up "can help / can't do" tiles (fork look), on our glass tokens.
            // IntrinsicSize.Min sizes the Row to the taller tile and both fill it,
            // so they stay a matched pair without either one cropping its body.
        }

        OStep.Language -> {
            // The device's own language, so "Detected on this device" is a fact
            // rather than a decoration. The prototype hardcodes English as both
            // the detected AND the selected row, which is why its screenshot
            // shows English ticked under a button reading "Continue in Hindi" —
            // two different answers to one question. Here the tick follows the
            // real selection and the caption follows the real locale.
            val deviceLang = remember { detectedLanguageId(java.util.Locale.getDefault().language) }
            var showAll by rememberSaveable { mutableStateOf(false) }
            // English and Hindi lead; the rest sit behind "View all languages",
            // matching ONB-01's shape without pretending the list is only two.
            val lead = remember { LANGUAGES.filter { it.id == "English" || it.id == "Hindi" } }
            val rest = remember { LANGUAGES.filterNot { it.id == "English" || it.id == "Hindi" } }
            Funnel(
                OStep.Language,
                stringResource(R.string.ob_language_eyebrow), stringResource(R.string.ob_language_title),
                stringResource(R.string.ob_language_sub),
                // "Continue in Hindi" — the CTA names what the next screen will
                // be written in, so the choice is confirmed before it applies.
                stringResource(
                    R.string.ob_language_continue_in,
                    stringResource(languageLabelRes(language) ?: R.string.ob_lang_english),
                ),
                onBack = { back() }, onPrimary = { next() },
                progress = 0.38f,
                barTitle = stringResource(R.string.ob_language_bar_title),
                onUrgent = { urgentSupport = true },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    lead.forEach { option ->
                        LanguageCard(
                            title = stringResource(option.labelRes),
                            subtitle = if (option.id == deviceLang) {
                                stringResource(R.string.ob_lang_detected)
                            } else {
                                ""
                            },
                            selected = language == option.id,
                        ) { language = option.id }
                    }
                    if (showAll) {
                        rest.forEach { option ->
                            LanguageCard(
                                title = stringResource(option.labelRes),
                                subtitle = if (option.id == deviceLang) {
                                    stringResource(R.string.ob_lang_detected)
                                } else {
                                    ""
                                },
                                selected = language == option.id,
                            ) { language = option.id }
                        }
                    } else {
                        LanguageCard(
                            title = stringResource(R.string.ob_lang_view_all),
                            // Names the languages actually behind the row. A
                            // generic "and more" hides whether the one you want
                            // is in there at all.
                            // map is inline (so stringResource is legal here);
                            // joinToString's transform is not.
                            subtitle = rest.map { stringResource(it.labelRes) }.joinToString(" · "),
                            selected = false,
                        ) { showAll = true }
                    }
                }
            }
        }

        OStep.State -> Funnel(
            OStep.State,
            stringResource(R.string.ob_state_eyebrow), stringResource(R.string.ob_state_title),
            stringResource(R.string.ob_state_sub),
            stringResource(R.string.common_continue), primaryEnabled = state != null, onBack = { back() }, onPrimary = { next() },
            progress = 0.50f,
            barTitle = stringResource(R.string.ob_state_bar_title),
            onUrgent = { urgentSupport = true },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                STATE_OPTIONS.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { option ->
                            StateOptionRow(
                                label = stringResource(option.labelRes),
                                subtitle = stateOptionSubtitle(option.id),
                                selected = state?.id == option.id,
                                modifier = Modifier.weight(1f),
                            ) { state = option }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        OStep.Reset -> ResetStep(
            onDone = { resetDone = true; next() },
            onSkip = { next() },
            onBack = { back() },
            onUrgent = { urgentSupport = true },
        )

        OStep.Reflection -> Funnel(
            OStep.Reflection,
            stringResource(R.string.ob_reflection_eyebrow), stringResource(R.string.ob_reflection_title),
            stringResource(R.string.ob_reflection_sub), "",
            onBack = { back() }, onPrimary = {}, primaryEnabled = false,
            barTitle = stringResource(R.string.ob_reflection_bar_title),
            onUrgent = { urgentSupport = true },
        ) {
            listOf(
                "↘" to stringResource(R.string.ob_reflection_calmer),
                "—" to stringResource(R.string.ob_reflection_same),
                "↗" to stringResource(R.string.ob_reflection_unsettled),
                "·" to stringResource(R.string.ob_reflection_skip),
            ).forEachIndexed { index, (icon, label) ->
                OnboardingFeatureCard(
                    icon, label,
                    if (index == 3) stringResource(R.string.ob_reflection_private) else "",
                    onClick = { next() },
                )
            }
        }

        OStep.Consent -> Funnel(
            OStep.Consent,
            stringResource(R.string.ob_consent_eyebrow), stringResource(R.string.ob_consent_title),
            stringResource(R.string.ob_consent_sub),
            stringResource(R.string.common_continue), onBack = { back() },
            // Passing the consent step unlocks anonymous telemetry (DPDP posture:
            // nothing is counted before this moment — Analytics.track no-ops).
            onPrimary = { Analytics.unlock(); next() },
            progress = 0.75f,
            onUrgent = { urgentSupport = true },
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InfoCardFill)
                    .border(1.dp, InfoCardStroke, RoundedCornerShape(18.dp)),
            ) {
                // All six categories, every time — DPDP "specific and informed":
                // nothing collected under a switch the user never saw.
                // Keys are the consent contract; labels/hints are display copy.
                val rows = listOf(
                    Triple("mood_history", stringResource(R.string.ob_consent_mood), stringResource(R.string.ob_consent_mood_hint)),
                    Triple("ai_memory", stringResource(R.string.ob_consent_ai), stringResource(R.string.ob_consent_ai_hint)),
                    Triple("journal_memory", stringResource(R.string.ob_consent_journal), stringResource(R.string.ob_consent_journal_hint)),
                )
                rows.forEachIndexed { index, (key, label, hint) ->
                    // B37: heightIn + wrapping hint — "specific and
                    // informed" must not end in "…" exactly where the choice
                    // is made (large font / longer locales).
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = InfoCardHint)
                        }
                        AppSwitch(checked = consent[key] == true, onCheckedChange = { consent[key] = it })
                    }
                    if (index < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(InfoCardDivider))
                }
            }
        }

        OStep.Notify -> Funnel(
            OStep.Notify,
            stringResource(R.string.ob_notify_eyebrow), stringResource(R.string.ob_notify_title),
            stringResource(if (resetDone) R.string.ob_notify_sub else R.string.ob_notify_sub_skipped),
            stringResource(R.string.ob_notify_cta), onBack = { back() }, onPrimary = { applyReminderChoice(); next() },
            progress = 0.88f,
            onUrgent = { urgentSupport = true },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NOTIFY.forEach { option ->
                    val selected = notify == option.id
                    OnboardingFeatureCard(
                        if (option.id == "none") "×" else "♧",
                        stringResource(option.labelRes),
                        if (option.id == "evening") stringResource(R.string.ob_notify_recommended) else "",
                        selected = selected,
                        trailingCheck = selected,
                        onClick = { notify = option.id },
                    )
                }
            }
        }

        OStep.Guest -> Funnel(
            OStep.Guest,
            stringResource(R.string.ob_guest_eyebrow), stringResource(R.string.ob_guest_title),
            stringResource(R.string.ob_guest_sub), stringResource(R.string.ob_guest_continue),
            onBack = { back() }, onPrimary = { next() },
            barTitle = stringResource(R.string.ob_guest_bar_title),
            onUrgent = { urgentSupport = true },
            secondary = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SecondaryOnboardingButton(stringResource(R.string.ob_guest_create)) { step = OStep.SignUp }
                    TextButton(onClick = { signIn = true }) {
                        Text(stringResource(R.string.ob_guest_sign_in), color = Periwinkle, fontWeight = FontWeight.Bold)
                    }
                }
            },
        ) {
            ReferenceCard(borderColor = Periwinkle.copy(alpha = .24f), fill = CardFill) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE5EDE3)), contentAlignment = Alignment.Center) {
                        Text("◇", color = Ok, style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ob_guest_private), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(stringResource(R.string.ob_guest_private_sub), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }

        OStep.Ready -> Funnel(
            OStep.Ready,
            stringResource(R.string.ob_ready_eyebrow), stringResource(R.string.ob_ready_title),
            stringResource(R.string.ob_ready_sub), stringResource(R.string.ob_ready_cta),
            onBack = { back() }, onPrimary = {
                Analytics.unlock()
                Session.continueAsGuest(context)
            },
            barTitle = stringResource(R.string.ob_ready_bar_title),
            onUrgent = { urgentSupport = true },
            titleCentered = true,
        ) {
            WelcomeOrb(Modifier.align(Alignment.CenterHorizontally), 150.dp)
        }

        OStep.Under18 -> Funnel(
            OStep.Under18,
            stringResource(R.string.ob_underage_eyebrow), stringResource(R.string.ob_underage_title),
            stringResource(R.string.ob_underage_sub), stringResource(R.string.ob_underage_urgent),
            onBack = { back() }, onPrimary = { urgentSupport = true },
            barTitle = stringResource(R.string.ob_underage_bar_title),
            onUrgent = { urgentSupport = true },
            secondary = {
                SecondaryOnboardingButton(stringResource(R.string.ob_underage_welcome)) { step = OStep.Welcome }
            },
        ) {
            ReferenceCard {
                Text(stringResource(R.string.ob_underage_card), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(stringResource(R.string.ob_underage_card_sub), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }

        OStep.SignUp -> AuthScreen(
            initialCreating = true,
            onBack = { back() },
            onAccountCreated = {
                Analytics.track("onboarding_done")
                runCatching { Api.attest() }
                // Consent is the one write here that must never fail silently
                // (DPDP integrity — we may not imply choices the server never
                // took). The funnel's own composition is gone by now, so: retry
                // once, and if it still doesn't land, flag it so Privacy tells
                // the user their setup choices didn't save.
                val consentBody = JSONObject().apply { consent.forEach { (k, v) -> put(k, v) } }
                val consentSaved = runCatching { Api.updateConsent(consentBody) }
                    .recoverCatching { Api.updateConsent(consentBody) }
                    .isSuccess
                runCatching { Session.prefPut(CONSENT_SYNC_FAILED_KEY, (!consentSaved).toString()) }
                val selectedState = state
                if (selectedState != null) {
                    runCatching {
                        Api.updateProfile(
                            JSONObject()
                                .put("goals", org.json.JSONArray().put(selectedState.goal))
                                .put("motivations", org.json.JSONArray().put(selectedState.motivation)),
                        )
                    }
                    runCatching {
                        Api.checkIn(selectedState.mood, onboardingMoodNote(selectedState.mood), "sparkles", 3)
                    }
                }
            },
        )
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, onSignIn: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(WelcomeGradientTop, WelcomeGradientBottom)))
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        val compact = maxHeight < 720.dp
        WelcomeOrb(
            Modifier.align(Alignment.TopCenter).padding(top = if (compact) 92.dp else 146.dp),
            if (compact) 170.dp else 190.dp,
        )

        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 42.dp)
                .offset(y = if (compact) 40.dp else 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.ob_welcome_title),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 40.sp, lineHeight = 41.sp, letterSpacing = (-0.6).sp,
                ),
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(23.dp))
            Text(
                stringResource(R.string.ob_welcome_sub),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.5.sp, lineHeight = 24.sp),
                color = WelcomeTitleText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(15.dp))
            Text(
                stringResource(R.string.ob_welcome_privacy),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = WelcomeSubtitleText,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                    // B68: the shared PrimaryButton sets the role; the
                    // funnel's own CTAs now do too.
                    .background(PrimaryButtonFill)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onStart),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Air, null, tint = PrimaryButtonInk, modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.ob_welcome_cta), style = MaterialTheme.typography.titleMedium, color = PrimaryButtonInk)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignIn) {
                Text(stringResource(R.string.auth_have_account), style = MaterialTheme.typography.titleSmall, color = WelcomeSecondaryText)
            }
        }
    }
}

@Composable
private fun WelcomeOrb(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension * 0.38f
        drawCircle(
            Brush.radialGradient(
                0.55f to WelcomeOrbHaloInner, 0.82f to WelcomeOrbHaloOuter, 1f to Color.Transparent,
                center = center, radius = this.size.minDimension / 2f,
            ),
            this.size.minDimension / 2f,
            center,
        )
        drawCircle(WelcomeOrbHaloDisc, radius * 1.27f, center)
        drawCircle(
            Brush.radialGradient(
                0f to Color.White, 0.22f to WelcomeOrbMid, 1f to WelcomeOrbEdge,
                center = Offset(center.x - radius * 0.2f, center.y - radius * 0.27f),
                radius = radius * 1.55f,
            ),
            radius,
            center,
        )
    }
}

@Composable
private fun ResetStep(onDone: () -> Unit, onSkip: () -> Unit, onBack: () -> Unit, onUrgent: () -> Unit) {
    // Keep the same quiet bed as the full-screen reset. DisposableEffect inside
    // this helper stops it as soon as onboarding advances, skips, or goes back.
    ToolAmbienceEffect(R.raw.ambient_bed)
    // The orb, count and Reduce-Motion behaviour all come from the shared
    // BreatheEngine (Reset preset: four in, four out, no holds) — the same
    // engine every breathe surface in the app hosts.
    Funnel(
        OStep.Reset,
        stringResource(R.string.ob_reset_eyebrow), stringResource(R.string.ob_reset_title),
        stringResource(R.string.ob_reset_sub),
        stringResource(R.string.ob_reset_cta), onBack = onBack, onPrimary = onDone,
        progress = 0.62f,
        compactTitle = false,
        barTitle = stringResource(R.string.ob_reset_bar_title),
        onUrgent = onUrgent,
        secondary = {
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(26.dp))
                    .background(ResetDoneFill).clickable(onClick = onSkip),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.ob_reset_skip), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
        },
    ) {
        BreatheEngine(
            preset = BreathePreset.Reset,
            modifier = Modifier.fillMaxWidth(),
            chimeOn = true,
            compact = true,
        )
    }
}

// ── Shared bits ──────────────────────────────────────────────────────────

/** The last funnel fraction shown, so the next step's bar animates from it
 * (each step is a separate composition inside AnimatedContent). Cosmetic only. */
private object FunnelProgressMemory { var last = 0f }

@Composable
private fun Funnel(
    step: OStep,
    eyebrow: String,
    title: String,
    sub: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onBack: (() -> Unit)?,
    /** Explicit step fraction — the bar used to key off the ENGLISH eyebrow
     * copy, which broke the moment the eyebrows localized (i18n pass 2). */
    progress: Float = 1f,
    primaryEnabled: Boolean = true,
    titleCentered: Boolean = false,
    compactTitle: Boolean = false,
    /** The app-bar title, which is NOT the h1. The prototype names the step in
     * the bar ("Choose language") and asks the question in the heading ("Choose
     * what feels natural."); collapsing them loses the step's name the moment
     * the heading is a sentence. Defaults to the eyebrow for steps not yet
     * given their own bar copy. */
    barTitle: String = eyebrow,
    /** Opens crisis support. Null only where the prototype also omits it. */
    onUrgent: (() -> Unit)? = null,
    secondary: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = funnelProgress(step)
    val stepIndex = funnelStepIndex(step)
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(FunnelHeaderTop, FunnelHeaderBottom)))
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(bottom = if (secondary == null) 118.dp else 182.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── App bar (ONB-xx chrome) ─────────────────────────────────────
            // Back · title + "N of 10" · urgent support. The funnel used to open
            // straight onto its eyebrow with no bar at all: there was no way out
            // but the system back gesture, nothing said how long this was going
            // to take, and — the part that matters — crisis support was the one
            // door onboarding did not have. The prototype puts it on every step
            // except the two where it would be the only thing on screen.
            OnboardingAppBar(
                title = barTitle,
                stepIndex = stepIndex,
                onBack = onBack,
                onUrgent = onUrgent,
            )
            // ── Stepper ─────────────────────────────────────────────────────
            // Ten discrete segments rather than one sliding bar. A continuous
            // fill answers "how far along am I" with a vague fraction; the
            // prototype answers it with a countable number of steps, which is
            // the honest version when the total is fixed and small.
            OnboardingStepper(stepIndex)
            Column(
                Modifier.padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 1.2.sp),
                // Rose, not the quiet grey: in Light Dawn the eyebrow is the one
                // warm accent above the title, and EyebrowMuted made every
                // onboarding step open on two greys stacked.
                color = Warm,
            )
            Text(
                title,
                modifier = if (titleCentered) Modifier.fillMaxWidth() else Modifier,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = OnboardingSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = if (compactTitle) 34.sp else 40.sp,
                    lineHeight = if (compactTitle) 36.sp else 39.sp,
                    letterSpacing = (-0.4).sp,
                ),
                // Themed, not Color.White: the funnel follows the appearance
                // choice now, and a white title on the Dawn cream was invisible
                // (caught on the emulator). Night renders near-white as before.
                color = TextPrimary,
                textAlign = if (titleCentered) TextAlign.Center else TextAlign.Start,
            )
            if (sub.isNotBlank()) Text(
                sub,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.5.sp, lineHeight = 24.sp),
                color = FunnelBodyText,
            )
            Spacer(Modifier.height(2.dp))
            content()
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The sliding progress bar moved to the top as a ten-segment stepper
            // (OnboardingStepper). Keeping both would have stated the same fact
            // twice, in two different visual languages, on every step.
            if (primaryLabel.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = Periwinkle.copy(alpha = .22f), spotColor = Periwinkle.copy(alpha = .22f)).clip(RoundedCornerShape(28.dp))
                        .background(if (primaryEnabled) PrimaryButtonFill else PrimaryButtonDisabledFill)
                        .clickable(enabled = primaryEnabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onPrimary),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(primaryLabel, style = MaterialTheme.typography.titleSmall, color = PrimaryButtonInk, fontWeight = FontWeight.Bold)
                }
            }
            if (secondary != null) {
                Spacer(Modifier.height(12.dp))
                secondary.invoke()
            }
        }
    }
}

/**
 * Map an ISO-639 device language to one of [LANGUAGES], or null when this app
 * ships nothing for it.
 *
 * Null is the important return. "Detected on this device" printed under English
 * for a Tamil phone is a small lie that costs trust on the first screen, so a
 * locale with no match here labels nothing rather than defaulting to English.
 * Pure, so it is unit-testable without a Configuration.
 */
internal fun detectedLanguageId(iso: String): String? = when (iso.lowercase()) {
    "en" -> "English"
    "hi" -> "Hindi"
    "pa" -> "Punjabi"
    "ta" -> "Tamil"
    else -> null
}

/**
 * ONB-01's option row: a paper card with a title, an optional caption, and
 * either a tick (selected) or a chevron (everything else).
 *
 * Replaces a wrapping row of chips. Chips could not carry the second line the
 * screen needs — "Detected on this device", or which languages sit behind
 * "View all" — and a five-chip wrap re-flowed differently in Hindi, so the
 * tap target for a given language moved between locales.
 */
@Composable
private fun LanguageCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(CardFill)
            .then(
                // The selected card wears a plum ring; the others carry no
                // border at all, so selection is the only thing an outline
                // means on this screen.
                if (selected) Modifier.border(2.dp, Periwinkle, shape) else Modifier,
            )
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = stringResource(R.string.common_selected_cd),
                tint = Periwinkle,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = PickRowChevron,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The onboarding app bar: back · step name + "N of 10" · urgent support.
 *
 * The urgent-support button is the reason this exists at all. Onboarding is the
 * one stretch of the app where someone has told us nothing yet, and it was also
 * the one stretch with no door to crisis resources — a user who arrived in a bad
 * state had to complete or abandon a ten-step funnel to reach the thing they
 * needed. It is drawn in the danger tone and sized like every other tap target.
 */
@Composable
private fun OnboardingAppBar(
    title: String,
    stepIndex: Int,
    onBack: (() -> Unit)?,
    onUrgent: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(CardFill)
            .border(width = 0.5.dp, color = ProgressTrack, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 30.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val backCd = stringResource(R.string.common_back)
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFF7EFF8))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onBack() }
                    .semantics { contentDescription = backCd },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = Periwinkle,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.size(44.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = OnboardingSerif, fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Step 0 is the launch screen, which is outside the count and must
            // not render "0 of 10".
            if (stepIndex > 0) Text(
                stringResource(R.string.onboarding_step_counter, stepIndex, ONBOARDING_STEPS),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
            )
        }
        if (onUrgent != null) {
            val urgentCd = stringResource(R.string.onboarding_urgent_cd)
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = 0.10f))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onUrgent() }
                    .semantics { contentDescription = urgentCd },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = Danger,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Ten segments, the completed ones filled.
 *
 * `aria-label`-equivalent lives on the row rather than each segment: a screen
 * reader announcing ten unlabelled bars is noise, while "step 3 of 10" is the
 * whole message.
 */
@Composable
private fun OnboardingStepper(stepIndex: Int) {
    if (stepIndex <= 0) return
    val label = stringResource(R.string.onboarding_step_counter, stepIndex, ONBOARDING_STEPS)
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 32.dp)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(ONBOARDING_STEPS) { i ->
            Box(
                Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            i < stepIndex - 1 -> Brush.horizontalGradient(listOf(Periwinkle, Periwinkle))
                            i == stepIndex - 1 -> Brush.horizontalGradient(listOf(Periwinkle, Cyan))
                            else -> Brush.horizontalGradient(listOf(ProgressTrack, ProgressTrack))
                        },
                    ),
            )
        }
    }
}

@Composable
private fun ReferenceCard(
    borderColor: Color = Color.Transparent,
    fill: Color = ChipFill,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(fill)
            .border(1.dp, borderColor, RoundedCornerShape(17.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        content = content,
    )
}

@Composable
private fun StateOptionRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.heightIn(min = 116.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) Periwinkle else Color(0xFFF6EFF8))
            .clickable(onClick = onClick).padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (selected) Color.White.copy(alpha = .14f) else CardFill),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (selected) "≈" else "·", color = if (selected) Color.White else Periwinkle,
                style = MaterialTheme.typography.titleLarge)
        }
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                color = if (selected) Color.White.copy(alpha = .82f) else TextMuted)
        }
    }
}

@Composable
private fun SecondaryOnboardingButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(17.dp))
            .background(CardFill).border(1.dp, ProgressTrack, RoundedCornerShape(17.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Periwinkle)
    }
}

@Composable
private fun OnboardingFeatureCard(
    glyph: String,
    title: String,
    subtitle: String,
    selected: Boolean = false,
    trailingCheck: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(21.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 70.dp).clip(shape)
            .background(if (selected) Color(0xFFF1E6F2) else CardFill)
            .border(if (selected) 2.dp else 1.dp, if (selected) Periwinkle else ProgressTrack, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(FieldFill), contentAlignment = Alignment.Center) {
            Text(glyph, color = Periwinkle, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        if (trailingCheck) Icon(Icons.Outlined.Check, null, tint = Periwinkle, modifier = Modifier.size(20.dp))
        else if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = Periwinkle, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun stateOptionSubtitle(id: String): String = stringResource(
    when (id) {
        "stressed" -> R.string.ob_state_sub_clear
        "night" -> R.string.ob_state_sub_tired
        "overthinking" -> R.string.ob_state_sub_anxious
        "doubt" -> R.string.ob_state_sub_low
        "distant" -> R.string.ob_state_sub_overwhelmed
        else -> R.string.ob_state_sub_unsure
    },
)

/** One side of the two-up disclosure — a glass tile with an accent heading.
 *
 * Grows to fit its body, never crops it. It used to be a fixed `.height(129.dp)`,
 * and on a 720px device the "Can't do" tile cut the word "emergencies" in half —
 * the single most important limitation on the screen whose entire job is stating
 * limitations, silently truncated, in the shortest locale we ship. The caller
 * pairs these in a Row at [IntrinsicSize.Min] so both sides still match height.
 */
@Composable
private fun DisclosureTile(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.heightIn(min = 129.dp).clip(RoundedCornerShape(17.dp)).background(ChipFill)
            .border(1.dp, PickCardStroke, RoundedCornerShape(17.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // `accent` tints the heading — it was accepted and then never applied, so
        // "Can help" and "Can't do" rendered identically in plain white despite
        // the call site passing Cyan and TextSoft to tell them apart.
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp), color = accent)
        Text(body, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = FunnelBodyText)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ChipWrap(options: List<String>, selected: String?, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        options.forEach { opt ->
            val isSelected = selected == opt
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.heightIn(min = 48.dp).clip(shape)
                    .background(if (isSelected) ChipSelectedFill else DotUnselectedFill)
                    .border(
                        1.dp,
                        if (isSelected) PickCardStroke else PickRowStroke.copy(alpha = 0.35f),
                        shape,
                    )
                    // B48: selectable() so TalkBack hears WHICH chip is
                    // chosen — Role.RadioButton alone announced state-less
                    // radio buttons on the language/reminder/scale pickers.
                    .selectable(
                        selected = isSelected,
                        role = androidx.compose.ui.semantics.Role.RadioButton,
                        onClick = { onPick(opt) },
                    )
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    opt,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) ChipSelectedInk else TextPrimary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** [ChipWrap] over stable-id options: the chips show localized copy while the
 * caller keeps branching on the id (reminder hour, language code). */
@Composable
internal fun ChipWrapOptions(options: List<PickOption>, selectedId: String?, onPick: (String) -> Unit) {
    val labels = options.map { stringResource(it.labelRes) }
    val selectedLabel = options.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }?.let { labels[it] }
    ChipWrap(labels, selectedLabel) { label ->
        labels.indexOf(label).takeIf { it >= 0 }?.let { onPick(options[it].id) }
    }
}
