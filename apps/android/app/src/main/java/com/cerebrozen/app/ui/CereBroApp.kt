package com.cerebrozen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import com.cerebrozen.app.R
import com.cerebrozen.app.BuildConfig
import com.cerebrozen.app.audio.MediaCatalog
import com.cerebrozen.app.audio.Sfx
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.screens.AccountDeletionScreen
import com.cerebrozen.app.ui.screens.BaselineScreen
import com.cerebrozen.app.ui.screens.ExploreScreen
import com.cerebrozen.app.ui.screens.PracticeLibraryScreen
import com.cerebrozen.app.ui.screens.PracticeBreathingScreen
import com.cerebrozen.app.ui.screens.GratitudeReflectionScreen
import com.cerebrozen.app.ui.screens.UrgentSupportScreen
import com.cerebrozen.app.ui.screens.GroundingScreen
import com.cerebrozen.app.ui.screens.GroundingIntroScreen
import com.cerebrozen.app.ui.screens.CheckInDetailScreen
import com.cerebrozen.app.ui.screens.WeeklyInsightsScreen
import com.cerebrozen.app.ui.screens.ReferenceSleepInsightsScreen
import com.cerebrozen.app.ui.screens.AuroraBackground
import com.cerebrozen.app.ui.screens.SceneVideo
import com.cerebrozen.app.ui.breathing.BreathLoopsScreen
import com.cerebrozen.app.ui.breathing.BreathPattern
import com.cerebrozen.app.ui.offline.BodyScanScreen
import com.cerebrozen.app.ui.offline.CbtIOfflineScreen
import com.cerebrozen.app.ui.offline.CrisisGroundingScreen
import com.cerebrozen.app.ui.offline.GuidedImageryScreen
import com.cerebrozen.app.ui.offline.InsightReelScreen
import com.cerebrozen.app.ui.offline.MbctOfflineScreen
import com.cerebrozen.app.ui.games.MindfulGameScreen
import com.cerebrozen.app.ui.games.MindfulGamesScreen
import com.cerebrozen.app.ui.screens.BreathingScreen
import com.cerebrozen.app.ui.screens.BubblePopScreen
import com.cerebrozen.app.ui.screens.Celebration
import com.cerebrozen.app.ui.screens.Celebrations
import com.cerebrozen.app.ui.screens.CbtReframeScreen
import com.cerebrozen.app.ui.screens.IntentionScreen
import com.cerebrozen.app.ui.screens.OneGoodThingScreen
import com.cerebrozen.app.ui.screens.CompanionStyleScreen
import com.cerebrozen.app.ui.screens.LanguageScreen
import com.cerebrozen.app.ui.screens.NotificationInboxScreen
import com.cerebrozen.app.ui.screens.CrisisRegionScreen
import com.cerebrozen.app.ui.screens.DataExportScreen
import com.cerebrozen.app.ui.screens.GuidedImageryScreen
import com.cerebrozen.app.ui.screens.HumanSupportScreen
import com.cerebrozen.app.ui.screens.JournalScreen
import com.cerebrozen.app.ui.screens.Onboarding
import com.cerebrozen.app.ui.screens.PatternScreen
import com.cerebrozen.app.ui.screens.TrendsScreen
import com.cerebrozen.app.ui.screens.PlanScreen
import com.cerebrozen.app.ui.screens.PlayerScreen
import com.cerebrozen.app.ui.screens.GoalsScreen
import com.cerebrozen.app.ui.screens.RitualBuilderScreen
import com.cerebrozen.app.ui.screens.SafetyPlanScreen
import com.cerebrozen.app.ui.screens.SearchScreen
import com.cerebrozen.app.ui.screens.PremiumScreen
import com.cerebrozen.app.ui.screens.PrivacyPolicyScreen
import com.cerebrozen.app.ui.screens.PrivacyScreen
import com.cerebrozen.app.ui.screens.ProgramsScreen
import com.cerebrozen.app.ui.screens.RemindersScreen
import com.cerebrozen.app.ui.screens.SleepScreen
import com.cerebrozen.app.ui.screens.SoundsScreen
import com.cerebrozen.app.ui.screens.TalkScreen
import com.cerebrozen.app.ui.screens.TrustedContactScreen
import com.cerebrozen.app.ui.screens.WorkCoachScreen
import com.cerebrozen.app.ui.screens.TippScreen
import com.cerebrozen.app.ui.screens.TodayScreen
import com.cerebrozen.app.ui.screens.AuthScreen
import com.cerebrozen.app.ui.screens.ToolkitScreen
import com.cerebrozen.app.ui.screens.WindDownRitualScreen
import com.cerebrozen.app.ui.screens.YouScreen
import com.cerebrozen.app.ui.screens.AppearanceScreen
import com.cerebrozen.app.ui.theme.AppTheme
import com.cerebrozen.app.ui.theme.NavPillBottom
import com.cerebrozen.app.ui.theme.NavPillTop
import com.cerebrozen.app.ui.theme.NavScrim
import com.cerebrozen.app.ui.theme.LavenderPillTop
import com.cerebrozen.app.ui.theme.LavenderPillFloor
import com.cerebrozen.app.ui.theme.Stroke
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.OnPrimary
import com.cerebrozen.app.ui.theme.VeilStrong
import com.cerebrozen.app.ui.theme.themeModeFromPref

// W24: the tabs wear the hand-drawn orb-family line icons (res/drawable/ic_tab_*)
// instead of stock Material glyphs — one consistent 2dp rounded-line set.
//
// W25 history (docs/REDESIGN_V2_2026-08-06-lightdawn.md §3.1/§6.1): the
// Light-Dawn ruling demoted Sleep to a pushed screen and gave Explore the
// slot. Home became **Today** in that pass — label and icon only; the route
// stays `home` and the enum constant keeps its name so ~30 call sites,
// deeplinks and saved back-stack entries keep working.
//
// Urgent support does NOT depend on the tab set and never did: since V2-a the
// shield sits in the top bar of every frame (Page/PremiumPage/SubPage), and
// the You tab keeps its Support card (verified in NavigationChromeTest).
// V2-d (REDESIGN_V2 §2, owner-approved 2026-08-15): Sleep takes the tab back
// and Explore retires. This REVERSES the earlier five-tab ruling deliberately:
// Sleep is the only bias-robust evidence domain (F2, g=0.71) and had no
// permanent door, while Explore held a slot for browsing — the behaviour the
// V2 research found distressed users abandon ("relief, not discovery"). The
// library survives as the practices hub behind Today's "more options →"; the
// `explore` route stays registered for deeplinks.
// V3-a (owner-approved 2026-08-16, companion-first prototype): THREE tabs —
// Home · Chat · Sleep. The conversation is the flagship and the app opens on
// it; Home is the one-scroll summary; Sleep keeps its room. Journal is now a
// chat tool + a room doored from Home (route stays registered); You lives
// behind the gear in every tab root's top bar (CereBroTopBar.onSettings).
// Enum constant names Home/Talk keep their historic spelling — routes,
// deeplinks and ~30 call sites depend on "home"/"talk".
internal enum class Tab(val route: String, @androidx.annotation.StringRes val labelRes: Int, @androidx.annotation.DrawableRes val icon: Int) {
    Home("home", R.string.tab_home, R.drawable.ic_tab_today),
    Talk("talk", R.string.tab_talk, R.drawable.ic_tab_talk),
    Sleep("sleep", R.string.tab_sleep, R.drawable.ic_tab_sleep),
}

internal fun shouldShowBottomBar(route: String?): Boolean =
    // V2-d: `sleep` is a tab root again; `explore` is the pushed screen now
    // (deeplink-only), so it leaves this set for the same reason sleep once
    // did — a pill with five unlit tabs says nothing about where you are.
    // V2-e: the talk/live, talk/chat and dailyplan aliases left the graph.
    // V3-a: `you` and `reminders` leave the set — the settings family is a
    // full-screen push behind the gear now, and a pill under a settings room
    // would light nothing. `journal` keeps the pill and lights Home (it is a
    // content room doored from Home, like gratitude).
    route in setOf("home", "sleep", "practice-library", "gratitude", "sleepinsights", "talk", "journal", "groundingintro", "checkin", "notifications", "insights", "trends", "patterns", "goals", "baseline")

/**
 * Resolve a notification deeplink to an in-app route, or null to stay Home.
 *
 * The server's nudge vocabulary is `cerebro://mood|breathe|sleep|insights`
 * (backend services/nudges.py + digest.py), and admins can author free-form
 * links — so this is an allowlist, not a passthrough: a notification must
 * never navigate to an arbitrary graph node. Push.kt has attached this URI to
 * the launch intent since FCM landed; until now nothing read it, so every
 * nudge dumped the user on Home regardless of what it promised (audit A1).
 */
internal fun routeForDeeplink(raw: String?): String? {
    val uri = (raw ?: "").trim()
    if (!uri.startsWith("cerebro://")) return null
    val path = uri.removePrefix("cerebro://").trim('/').lowercase(java.util.Locale.ROOT)
    val resolved = when (path) {
        // Nudge vocabulary → where the promise actually lives.
        "mood", "checkin" -> "home"
        "breathe" -> "breathe/reset"
        "chat", "oracle" -> "talk"
        else -> path
    }
    return resolved.takeIf { it in EXTERNAL_ROUTES }
}

/**
 * Every route something OUTSIDE the app may ask for by name.
 *
 * Two callers, deliberately the same set: [routeForDeeplink] (a notification's
 * `cerebro://` URI) and [NotificationLog.routeFor] (the destination the inbox's
 * "Open" button hands to `navigate()`). Both take a string chosen elsewhere —
 * by the server, or by a nudge kind — and turn it into navigation, so both are
 * bound by the same rule: it must be a destination that exists.
 *
 * Keeping one set rather than two is the point. `routeFor` had its own private
 * mapping and emitted "today", a route the graph has never defined, which meant
 * every check-in nudge in the inbox led to a crash.
 */
internal val EXTERNAL_ROUTES = setOf(
    "home", "explore", "sleep", "talk", "journal", "journal/new", "you",
    "insights", "trends", "toolkit", "crisis", "safetyplan",
    "sounds", "sounds/mixer", "winddown", "plan", "goals", "programs",
    "breathe/reset", "breathe/box",
)

/** The pending notification route, offered by MainActivity (launch intent or
 * onNewIntent) and consumed by the signed-in NavHost once it exists. */
object DeeplinkBus {
    var pending by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    fun offer(raw: String?) { routeForDeeplink(raw)?.let { pending = it } }
    fun clear() { pending = null }
}

// The Sleep-stays-Night forcing (SLEEP_CONTEXT_ROUTES + AppTheme.forceNight)
// was REMOVED here on 2026-08-04 by OWNER DECISION, recorded in docs/TODO.md:
// appearance is global, on every client in the same change — web unwrapped its
// .theme-night sleep scope in this same commit; iOS already conformed. The
// hardware concern it served (a bright player mid-wind-down) is answered by
// the user's own theme choice: Night remains one tap away in Appearance.

/**
 * Whether the nav pill is drawn right now.
 *
 * It is hidden while the keyboard is up, and that is the whole point: the pill
 * reserves its slot in the Scaffold whether or not it has anything to draw, so
 * with the IME open there was a dead lavender band sitting between the keyboard
 * and the composer on every screen you can type on. Worse, the tabs it showed
 * were unreachable — tapping one dismisses the keyboard first.
 *
 * Kept as a pure function of the two inputs so the matrix is a unit test rather
 * than something only a device can tell you (`NavigationChromeTest`); the
 * composable below additionally checks the live inset itself
 * (`BottomNavImeTest`), so both layers of the rule are pinned.
 */
internal fun navVisible(route: String?, imeOpen: Boolean): Boolean =
    shouldShowBottomBar(route) && !imeOpen

/**
 * The floating tab bar — and the rule that it yields to the keyboard.
 *
 * With the IME up this emits **nothing**, so Scaffold reserves no bottom slot for
 * it. It used to emit the pill unconditionally: the keyboard drew over it, but
 * Scaffold still charged the content that height, and every screen body already
 * carries `imePadding()` (see Common.kt `Page`). The two stacked, so a composer
 * floated ~78dp above the keyboard with an empty band under it — worst on Talk and
 * Journal, the two screens where you type most. Nav that hides while typing is
 * also the Material behaviour, and typing is the one moment nobody is navigating.
 *
 * [imeVisible] is a parameter, defaulting to the real inset, so the rule is
 * renderable off-device (`BottomNavImeTest`).
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BottomNavBar(
    currentRoute: String,
    compact: Boolean,
    imeVisible: Boolean = WindowInsets.isImeVisible,
    onSelect: (Tab) -> Unit,
) {
    if (imeVisible) return
    // A floating lavender pill over a dark scrim — the tabs read as a lifted
    // capsule rather than a flat system bar.
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, NavScrim.copy(alpha = 0.96f))))
            // Edge-to-edge does not reserve Android's gesture/three-button
            // navigation area for this custom bar; keep the app tabs above it.
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 3.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                // The pill's lift comes from the theme's shadow tokens, not a
                // hardcoded 40% black: on Dawn a black drop under an ivory
                // capsule reads as a grey smudge (CardShadow is warm-plum).
                .shadow(
                    18.dp, RoundedCornerShape(32.dp),
                    ambientColor = com.cerebrozen.app.ui.theme.CardShadow.navAmbient,
                    spotColor = com.cerebrozen.app.ui.theme.CardShadow.navSpot,
                )
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(NavPillTop.copy(alpha = 0.96f), NavPillBottom.copy(alpha = 0.98f)),
                    ),
                )
                .border(1.dp, Stroke.navPill, RoundedCornerShape(32.dp))
                .padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                BottomTabItem(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

/** One tab in the floating pill nav: a rounded cell that lights up with a soft
 * lavender radial + hairline when selected. Icons/labels brighten on selection
 * (TextPrimary vs TextMuted2) so the active tab reads without relying on colour
 * alone. [compact] tightens sizes on narrow phones. */
@Composable
private fun BottomTabItem(
    tab: Tab,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) OnPrimary else TextMuted2
    // W10: the icon settles in with a soft spring on becoming selected (0.9 → 1.0);
    // unselected icons rest a whisper smaller. Reduce Motion holds every icon
    // steady at full size (static, never blank).
    val reduceMotion = com.cerebrozen.app.ui.screens.rememberReduceMotion()
    val springScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "tab-icon-spring",
    )
    val iconScale = if (reduceMotion) 1f else springScale
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) {
                    Brush.linearGradient(listOf(LavenderPillTop, LavenderPillFloor))
                } else {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .border(
                1.dp,
                if (selected) Color.White.copy(alpha = 0.20f) else Color.Transparent,
                RoundedCornerShape(20.dp),
            )
            // TalkBack must announce these as tabs, and say which one is
            // selected — a bare clickable Column announces neither.
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val label = stringResource(tab.labelRes)
        Box(
            Modifier
                .size(if (compact) 30.dp else 34.dp)
                .clip(CircleShape)
                .background(if (selected) VeilStrong else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(tab.icon),
                // The label Text below carries the name; a description here
                // would make TalkBack say it twice inside the tab node.
                contentDescription = null,
                tint = tint,
                // Thin 2dp-line icons carry far less visual weight than filled
                // glyphs — owner feedback (2026-07-13): 18dp read tiny on device.
                // 22dp (20 compact) matches the perceived size of the old set.
                modifier = Modifier.size(if (compact) 20.dp else 22.dp).scale(iconScale),
            )
        }
        Text(
            label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** W24 D3: Night↔Dawn changes glide instead of snapping. A Crossfade around
 * the themed tree would recreate the NavHost (destroying navigation state), so
 * instead a full-screen wash of the NEW theme's backdrop appears the instant
 * the preference flips and fades away over 350ms — the re-tokened screen
 * emerges from a calm solid, never a hard cut. Keyed on the *preference*-
 * resolved theme (Appearance choice / system dark).
 * Reduce Motion: no scrim — the honest instant snap. */
@Composable
private fun ThemeGlideScrim() {
    val reduceMotion = com.cerebrozen.app.ui.screens.rememberReduceMotion()
    val prefNight = when (AppTheme.mode) {
        com.cerebrozen.app.ui.theme.ThemeMode.System -> AppTheme.systemDark
        com.cerebrozen.app.ui.theme.ThemeMode.Night -> true
        com.cerebrozen.app.ui.theme.ThemeMode.Dawn -> false
    }
    var seen by remember { mutableStateOf(prefNight) }
    val veil = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(prefNight) {
        if (seen != prefNight) {
            seen = prefNight
            if (!reduceMotion) {
                veil.snapTo(1f)
                veil.animateTo(0f, tween(350))
            }
        }
    }
    // Night resolves against the NEW theme, so the wash is the destination's
    // own backdrop. The Box never consumes input; it is purely a veil.
    if (veil.value > 0f) {
        Box(Modifier.fillMaxSize().background(com.cerebrozen.app.ui.theme.Night.copy(alpha = veil.value)))
    }
}

/** Keeps the status/navigation-bar icon appearance in step with the theme:
 * light icons over Night, dark icons over Dawn.
 *
 * It reads the resolved appearance during composition so the icon contrast
 * changes together with the selected theme.
 */
@Composable
private fun SyncSystemBarIcons() {
    val view = androidx.compose.ui.platform.LocalView.current
    val lightBars = !AppTheme.isNight
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            // Unwrap ContextThemeWrapper layers to find the host Activity's window.
            var ctx = view.context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is android.app.Activity) break
                ctx = ctx.baseContext
            }
            (ctx as? android.app.Activity)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
    }
}


@Composable
fun CereBroApp() {
    // Dusk & Dawn wiring (REDESIGN §4.1): feed the system dark/light signal in,
    // restore the persisted preference once, and keep the bar icons in step.
    AppTheme.systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    // Wind-down hours need the clock, and the clock moves while the app is open —
    // someone reading at 20:58 should not still be on a bright screen at 21:30.
    LaunchedEffect(Unit) {
        while (true) {
            AppTheme.hour = java.time.LocalTime.now().hour
            delay(60_000)
        }
    }
    // Restore the persisted Appearance choice exactly once. A `remember { … }`
    // calc lambda must stay side-effect free (Compose may run it speculatively),
    // so the write lives in a LaunchedEffect like the splash below.
    LaunchedEffect(Unit) { AppTheme.mode = themeModeFromPref(Session.prefGet("theme_mode")) }

    // A brief branded splash on cold launch — always Night (brand moment).
    // Reduce Motion gets the settled frame instantly, so holding it for the full
    // animation length would just be a longer dead screen: shorten the hold too.
    // Production screens follow the selected appearance. Clear the internal
    // preview/test override before any early return.
    AppTheme.forceNight = false
    val splashReduceMotion = com.cerebrozen.app.ui.screens.rememberReduceMotion()
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(splashReduceMotion) {
        delay(if (splashReduceMotion) 450 else 1100)
        showSplash = false
    }
    if (showSplash) {
        SyncSystemBarIcons()
        Splash()
        return
    }

    // Signed-out: the whole app is the onboarding/auth flow (live backend session,
    // same account as iOS/web). Session.signedIn is Compose-observable. The funnel's
    // bespoke night art doesn't theme, so it is always Night.
    if (!Session.signedIn && !Session.guestMode) {
        SyncSystemBarIcons()
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            AuroraBackground()
            Onboarding()
        }
        return
    }

    // An authenticated session is an established relationship — unlock the
    // anonymous, opt-out telemetry for returning users who never walked the
    // new consent-gated funnel (DPDP posture, owner decision 2026-07-13).
    LaunchedEffect(Unit) { com.cerebrozen.app.net.Analytics.unlock() }

    // Three things that only make sense once signed in, all fire-and-forget:
    //  * flush anything the user wrote while offline, before any screen reads
    //    a list that would otherwise be missing their own entry;
    //  * re-register this install for push (FCM rotates tokens silently, so
    //    this runs on every cold start, not once);
    //  * resolve the server sound/video catalogue and warm the one-shot assets
    //    onto disk so taps fire with no network in the path — best-effort, every
    //    sound falls back to its synthesized tone or bundled loop either way.
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        if (Session.signedIn) {
            runCatching { com.cerebrozen.app.net.Outbox.drain() }
            com.cerebrozen.app.notify.Push.register(appContext)
            runCatching { MediaCatalog.load(Api.mediaCatalog(), BuildConfig.API_BASE_URL) }
        }
        runCatching { Sfx.warm(appContext) }
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Tab.Talk.route
    // Honor the notification's promise: navigate to the deeplink the nudge
    // carried. Signed-out sessions never reach this point, so the route waits
    // on the bus until the NavHost exists.
    LaunchedEffect(DeeplinkBus.pending) {
        DeeplinkBus.pending?.let { route ->
            DeeplinkBus.clear()
            navController.navigate(route) { launchSingleTop = true }
        }
    }
    // The primary navigation belongs only to the five root destinations. Detail,
    // player, tool and game screens get the full viewport and one clear Back path.
    // WindowInsets.ime reports the keyboard's height; > 0 means it is up. Read
    // here rather than inside the bottomBar lambda so the Scaffold recomposes
    // and reclaims the slot, instead of keeping an empty one.
    val imeOpen = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val showBottomBar = navVisible(current, imeOpen)
    SyncSystemBarIcons()
    val compactNav = LocalConfiguration.current.screenWidthDp < 380
    // Aurora hue shifts by section (sleep = violet, talk = cyan, else lavender).
    // E6: the accent cross-fades between tabs instead of snapping; Reduce Motion
    // keeps the honest instant snap.
    val reduceMotion = com.cerebrozen.app.ui.screens.rememberReduceMotion()
    val auroraAccent by animateColorAsState(
        targetValue = when (current) {
            "sleep", "sounds", "sounds/mixer", "winddown" -> com.cerebrozen.app.ui.theme.Accent.sleep
            Tab.Talk.route, "talk/live", "talk/chat" -> com.cerebrozen.app.ui.theme.Accent.talk
            else -> com.cerebrozen.app.ui.theme.Accent.home
        },
        animationSpec = if (reduceMotion) snap() else tween(600),
        label = "aurora-accent",
    )

    // The Sleep tab's scene loop, when one exists. It sits *beneath* the aurora, not
    // instead of it: the aurora is translucent, so an uploaded scene reads through it,
    // and with no scene uploaded (the shipping default — we hold no video we have the
    // rights to) the aurora is simply what the user sees, exactly as before.
    // Reduce Motion suppresses it entirely — a looping video is motion.
    // Reading `loaded` (Compose-observable) is what re-runs this once the catalogue
    // lands. It arrives asynchronously, after the first composition — a bare urlFor()
    // read touches no snapshot state, so without this the scene would stay missing
    // until some unrelated recomposition happened to occur.
    val catalogueLoaded = MediaCatalog.loaded
    val sceneUrl = when {
        !catalogueLoaded || reduceMotion -> ""
        // The Sleep screen only. The pushed "sounds"/mixer screens are built on
        // PremiumFrame, which paints its own opaque plate — a scene behind those
        // would decode and then be covered, burning a video decoder to render
        // nothing. Restrict it to the surface where it actually shows.
        current == "sleep" -> MediaCatalog.urlFor(MediaCatalog.Keys.SCENE_NIGHT_LAKE)
        else -> ""
    }

    Box(Modifier.fillMaxSize()) {
    SceneVideo(sceneUrl, Modifier.fillMaxSize())
    // The aurora's plate is the app's opaque page floor; it has to go sheer over a
    // scene, or the video under it can never be seen.
    AuroraBackground(accent = auroraAccent, sceneBehind = sceneUrl.isNotBlank())
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            // navVisible is the pure rule (tab routes only, never over the
            // keyboard); BottomNavBar re-checks the live IME inset itself, so
            // the guard holds even mid-frame while the inset animates.
            if (showBottomBar) {
            // The talk/* aliases render the Talk tab, so the pill highlights it.
            BottomNavBar(
                currentRoute = when {
                    current.startsWith("talk/") -> Tab.Talk.route
                    current == "groundingintro" || current == "checkin" || current == "notifications" || current == "insights" || current == "trends" || current == "patterns" || current == "dailyplan" || current == "goals" || current == "baseline" -> Tab.Home.route
                    // V2-d: the practice family is reached from Home, so those
                    // routes light Home; sleep insights lights Sleep.
                    // V3-a: journal lights Home (its doors live there now).
                    current == "practice-library" || current == "gratitude" || current == "journal" -> Tab.Home.route
                    current == "sleepinsights" -> Tab.Sleep.route
                    else -> current
                },
                compact = compactNav,
            ) { tab ->
                // One haptic vocabulary app-wide: the custom
                // Haptics object (see ui/Haptics.kt).
                if (current != tab.route) Haptics.selection()
                navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            // V3-a: the app opens on the conversation — "chat first" is the
            // owner's ruling, and the companion's opener is the new check-in.
            startDestination = Tab.Talk.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // A gentle shared-axis feel: cross-fade paired with a whisper of scale,
            // so screens settle in rather than hard-cut.
            enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(280)) + scaleIn(initialScale = 0.98f, animationSpec = tween(280)) },
            exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(170)) + scaleOut(targetScale = 1.02f, animationSpec = tween(170)) },
            popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(280)) + scaleIn(initialScale = 1.02f, animationSpec = tween(280)) },
            popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(170)) + scaleOut(targetScale = 0.98f, animationSpec = tween(170)) },
        ) {
            // Cross-tab links use the SAME pop/save/restore pattern as the tab
            // pill — a plain navigate() pushed duplicate tab entries, so back
            // from Home's avatar walked Home→Talk→Home instead of tab history
            // (audit A26). Non-tab routes push normally.
            val tabRoutes = Tab.entries.map { it.route }.toSet()
            val open: (String) -> Unit = { route ->
                // Routes reach here from DYNAMIC sources now — a logged
                // notification (NotificationLog.routeFor), a chat widget
                // (widgetRoute), a plan step, and the toolkit_recent pref — so
                // a stale or renamed one is a data problem, not a programming
                // error. navigate() throws IllegalArgumentException on an
                // unknown destination, which took the whole app down: a
                // check-in nudge pointed at "today" (the Today tab's route is
                // "home") and tapping Open crashed it.
                //
                // A door that does nothing is bad; a door that kills the app is
                // worse, and on a screen someone opened from a nudge.
                if (navController.graph.findNode(route) == null) {
                    android.util.Log.w("CereBroApp", "ignoring unknown route: $route")
                } else if (route in tabRoutes) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                } else {
                    navController.navigate(route) { launchSingleTop = true }
                }
            }
            val back: () -> Unit = { navController.popBackStack() }
            composable(Tab.Home.route) { TodayScreen(onOpen = open) }
            composable("groundingintro") {
                GroundingIntroScreen(
                    onBack = back,
                    onStart = { open("ground") },
                    onUrgent = { open("crisis") },
                )
            }
            composable("checkin") {
                CheckInDetailScreen(
                    onBack = back,
                    onSaved = { navController.popBackStack() },
                    onUrgent = { open("crisis") },
                )
            }
            // V2-d: Explore is deeplink-only (its tab went back to Sleep); the
            // route survives so `cerebro://explore` nudges keep their promise.
            composable("explore") { ExploreScreen(onOpen = open) }
            composable("practice-library") { PracticeLibraryScreen(onBack = back, onOpen = open) }
            composable("breathing-intro") {
                PracticeBreathingScreen(onBack = back, onUrgent = { open("crisis") }, onBegin = { open("breathe/reset") })
            }
            // Sleep is a pushed screen since the five-tab pass — it takes an
            // onBack so it carries a visible back door, not just the gesture.
            // V2-d: Sleep is a tab root — no back arrow; the brand mark takes
            // the slot (walk defect: the root wore a back door to Today).
            composable("sleep") { SleepScreen(onOpen = open) }
            composable(Tab.Talk.route) { TalkScreen(onOpen = open) }
            // The live/chat split belonged to the two-mode Talk screen. Talk is
            // one surface again — voice and typing live together, with the
            // composer pinned below the transcript — so both routes land on it
            // rather than dead-ending anything still pointing at them.
            // V2-e: the talk/live + talk/chat aliases are gone — one Talk, one route.
            composable("journal") { JournalScreen(onOpen = open) }
            // The Home check-in's "Say more" bridge lands in the composer, not the hub.
            composable("journal/new") { JournalScreen(startInEntry = true, onOpen = open, onExit = back) }
            composable("you") { YouScreen(onOpen = open) }
            // Guest mode is a real app shell, so authentication must be
            // reachable without signing the local session out first.
            composable("auth") {
                LaunchedEffect(Session.signedIn) {
                    if (Session.signedIn) navController.popBackStack()
                }
                AuthScreen(onBack = back)
            }
            composable("insights") { WeeklyInsightsScreen(onBack = back, onOpen = open) }
            composable("programs") { ProgramsScreen(onBack = back, onOpen = open) }
            // Work coaching — sponsored members only; the server enforces the
            // gate (403), the You row only SHOWS for them.
            composable("work") { WorkCoachScreen(onBack = back, onOpen = open) }
            composable("trustedcontact") { TrustedContactScreen(onBack = back) }
            // Sounds is the one audio hub (REDESIGN §3.4): Library + Mixer behind
            // a pill switch. `sounds/mixer` deep-links straight to the Mixer (the
            // old standalone `soundscape` route folded in here).
            composable("sounds") { SoundsScreen(onBack = back, onOpen = open) }
            composable("sounds/mixer") { SoundsScreen(onBack = back, onOpen = open, startInMixer = true) }
            // The player zooms in from the tapped card (iOS-18 zoom-transition feel).
            composable(
                "player",
                enterTransition = { scaleIn(initialScale = 0.85f, animationSpec = tween(320)) + fadeIn(tween(320)) },
                exitTransition = { scaleOut(targetScale = 0.9f, animationSpec = tween(200)) + fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(240)) + scaleIn(initialScale = 1.05f, animationSpec = tween(240)) },
                popExitTransition = { scaleOut(targetScale = 0.85f, animationSpec = tween(260)) + fadeOut(tween(260)) },
            ) { PlayerScreen(onBack = back, onOpen = open) }
            composable("plan") { PlanScreen(onBack = back, onOpen = open) }
            composable("search") { SearchScreen(onBack = back) }
            // patterns and trends open the REAL screens. Both were imported
            // and never routed while a Reference mock held the door — the same
            // shape as the practice screens, found on the parallel branch.
            composable("patterns") { PatternScreen(onBack = back) }
            composable("trends") { TrendsScreen(onBack = back) }
            // `dailyplan` points at PlanScreen rather than being deleted: any
            // stale link still lands on the real plan instead of nowhere.
            // V2-e: the dailyplan alias is gone — plan is the one route.
            // sleepinsights KEPT and linked from the Sleep rhythm line. It is
            // wired week/month/3-month charts with no twin, so deleting it
            // would drop a feature rather than a duplicate.
            composable("sleepinsights") { ReferenceSleepInsightsScreen(onBack = back, onOpen = open) }
            composable("safetyplan") { SafetyPlanScreen(onBack = back, onOpen = open) }
            composable("goals") { GoalsScreen(onBack = back, onOpen = open) }
            // Toolkit is the one activities hub (games + tools merged). The old
            // `games` and `tools` routes stay as aliases so Oracle widgets, plan
            // steps and saved deep-links keep landing somewhere real.
            composable("toolkit") { ToolkitScreen(onOpen = open, onBack = back) }
            composable("games") { MindfulGamesScreen(onBack = back) { open("mindfulgame/$it") } }
            composable("mindfulgame/{gameId}") { entry ->
                MindfulGameScreen(
                    gameId = entry.arguments?.getString("gameId"),
                    onBack = back,
                    onBackToGames = { navController.popBackStack("games", false) },
                )
            }
            // `tools` was a second route onto ToolkitScreen with nothing
            // pointing at it — a genuine duplicate of `toolkit` (12 call sites),
            // so it is gone rather than kept as a synonym nobody types.
            // The one parameterized breathe engine (box / two-minute reset).
            composable("breathe/box") { BreathLoopsScreen(onBack = back, onUrgent = { open("crisis") }) }
            // NOT a duplicate of `imagery`, despite the audit pairing them:
            // this is ui.offline.GuidedImageryScreen — four journeys (forest,
            // ocean, mountain, meadow) from OfflineToolContent with a TTS cue —
            // while `imagery` is ui.screens' single timed sequence in
            // Rituals.kt. Nothing navigates here, so a working feature is
            // sitting unreachable; which one should own the door is an IA
            // decision, not a cleanup, so the route stays until that is made.
            // See docs/TODO.md.
            composable("guidedimagery") { GuidedImageryScreen(onBack = back) }
            // The REAL body scan, not the static mock that used to sit here.
            // BodyScanScreen walks the eight parts in OfflineToolContent and
            // works with no network; PracticeBodyScanScreen drew a "2:41"
            // that never counted and a progress arc hardcoded to 180°, so the
            // screen looked like a running session and was a picture of one.
            // `body-scan-detail` went with it: the mock was its only entrance.
            composable("bodyscan") { BodyScanScreen(onBack = back) }
            composable("crisisgrounding") { CrisisGroundingScreen(onBack = back) { open("breathe/box") } }
            composable("insightreel") { InsightReelScreen(onBack = back) }
            composable("cbti") { CbtIOfflineScreen(onBack = back) }
            composable("mbct") { MbctOfflineScreen(onBack = back) }
            // Both breathing routes are the same screen now. It already carried
            // a Reset pattern — 4 in, 6 out, twelve rounds, which is the two
            // minutes the four surfaces pointing here promise — so `breathe/reset`
            // opens straight into it rather than showing a chooser in front of a
            // button that has already named what it gives you.
            composable("breathe/reset") {
                BreathLoopsScreen(onBack = back, startPattern = BreathPattern.Reset, onUrgent = { open("crisis") })
            }
            // The two guided routines (web parity): the Sleep tab's wind-down
            // and the Toolkit's Settle visualization.
            composable("winddown") { WindDownRitualScreen(onBack = back) }
            composable("imagery") { GuidedImageryScreen(onOpen = open, onBack = back) }
            composable("ritual") { RitualBuilderScreen(onBack = back) }
            composable("bubblepop") { BubblePopScreen(onBack = back) }
            composable("ground") { GroundingScreen(onBack = back) }
            // V2-e part 2: patternglow + zenripples (and the orphaned gratitude
            // garden) were deleted — the games REGISTRY versions (pattern-recall,
            // color-tap, still-point) are the one implementation per behavior.
            composable("gratitude") { GratitudeReflectionScreen(onBack = back, onUrgent = { open("crisis") }) }
            // The mock wrote four 1-10 sliders into a SharedPreferences file
            // called "personal_baseline" that nothing in the app reads. The
            // baseline card on Insights reads BaselineStore, which only the
            // real BaselineScreen writes — so saving your starting point could
            // never make the card appear. Scales differed too: 1-10 across four
            // dimensions in the mock, 1-5 across stress and sleep everywhere
            // else. Routed to the screen whose numbers are actually read.
            composable("baseline") { BaselineScreen(onBack = back) }
            composable("breathing") { BreathingScreen(onBack = back) }
            // NOT deleted, though nothing navigates here. These two were
            // deliberately salvaged from PR #2 and are pinned by
            // SalvagedToolsTest, which asserts their copy comes from resources
            // and each states why it works. Their doors were taken by the
            // journal composer's quick-entry chips (JournalScreen.kt:140) and
            // by widgetRoute mapping "one_good_thing"/"intention_set" to
            // "journal" — so the tools were superseded without anyone deciding
            // to retire them. Whether to relink or retire is an owner call, not
            // a nav cleanup; deleting tested, intentional work on that basis
            // would be the wrong way round.
            composable("onegoodthing") { OneGoodThingScreen(onBack = back) }
            composable("intention") { IntentionScreen(onBack = back) }
            // Both real tools, both previously shadowed by a mock on their own
            // route while sitting imported-but-unrouted a few lines above.
            // CbtReframeScreen writes a real journal entry through
            // JournalingTool; TippScreen is a four-step walkthrough that keeps
            // its place across rotation. `notice-change` is gone with the TIPP
            // mock that was its only entrance.
            composable("cbt") { CbtReframeScreen(onBack = back) }
            composable("tipp") { TippScreen(onBack = back, onUrgent = { open("crisis") }) }
            composable("crisis") { UrgentSupportScreen(onBack = back, onOpen = open) }
            composable("companion") { CompanionStyleScreen(onBack = back) }
            composable("language") { LanguageScreen(onBack = back) }
            composable("notifications") { NotificationInboxScreen(onBack = back, onOpen = open) }
            composable("appearance") { AppearanceScreen(onBack = back, onOpen = open) }
            // The REAL reminders screen, imported since the redesign and never
            // routed while a mock held the door. ReferenceRemindersScreen's
            // "Save reminder schedule" had an empty body — five toggles of
            // local `remember` state, four hardcoded times, and a button that
            // did nothing — so turning reminders on wrote no prefs, scheduled
            // no alarm, and asked for no notification permission. The inbox
            // reads reminder_on/reminder_hour, so it kept saying "no reminder
            // scheduled" while the switch sat on, and nothing ever fired.
            composable("reminders") { RemindersScreen(onBack = back) }
            composable("privacy") { PrivacyScreen(onBack = back, onOpen = open) }
            composable("premium") { PremiumScreen(onBack = back) }
            composable("crisisregion") { CrisisRegionScreen(onBack = back) }
            composable("humansupport") { HumanSupportScreen(onBack = back) }
            composable("privacypolicy") { PrivacyPolicyScreen(onBack = back) }
            composable("export") { DataExportScreen(onBack = back) }
            composable("delete") { AccountDeletionScreen(onBack = back) }
        }
    }
    // App-wide celebration flourish, above the nav chrome.
    if (Celebrations.active) Celebration(onFinished = { Celebrations.clear() })
    // W24 D3: the Appearance-change wash, above everything (it fades to nothing).
    ThemeGlideScrim()
    }
}
