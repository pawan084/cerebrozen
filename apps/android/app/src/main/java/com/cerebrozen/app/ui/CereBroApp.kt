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
import com.cerebrozen.app.ui.screens.PracticeTippScreen
import com.cerebrozen.app.ui.screens.NoticeChangeScreen
import com.cerebrozen.app.ui.screens.UntangleThoughtScreen
import com.cerebrozen.app.ui.screens.PracticeBodyScanScreen
import com.cerebrozen.app.ui.screens.BodyScanContentDetailScreen
import com.cerebrozen.app.ui.screens.GratitudeReflectionScreen
import com.cerebrozen.app.ui.screens.UrgentSupportScreen
import com.cerebrozen.app.ui.screens.GroundingScreen
import com.cerebrozen.app.ui.screens.GroundingIntroScreen
import com.cerebrozen.app.ui.screens.CheckInDetailScreen
import com.cerebrozen.app.ui.screens.WeeklyInsightsScreen
import com.cerebrozen.app.ui.screens.ReferenceTrendsScreen
import com.cerebrozen.app.ui.screens.ReferencePatternsScreen
import com.cerebrozen.app.ui.screens.ReferenceGoalsScreen
import com.cerebrozen.app.ui.screens.ReferenceGoalDetailScreen
import com.cerebrozen.app.ui.screens.ReferenceBaselineScreen
import com.cerebrozen.app.ui.screens.PatternDetailScreen
import com.cerebrozen.app.ui.screens.ReferenceDailyPlanScreen
import com.cerebrozen.app.ui.screens.ReferenceSleepInsightsScreen
import com.cerebrozen.app.ui.screens.ReferenceRemindersScreen
import com.cerebrozen.app.ui.screens.AuroraBackground
import com.cerebrozen.app.ui.screens.SceneVideo
import com.cerebrozen.app.ui.screens.BreathePreset
import com.cerebrozen.app.ui.screens.BreatheScreen
import com.cerebrozen.app.ui.breathing.BreathLoopsScreen
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
import com.cerebrozen.app.ui.screens.CrisisScreen
import com.cerebrozen.app.ui.screens.DataExportScreen
import com.cerebrozen.app.ui.screens.GratitudeGardenScreen
import com.cerebrozen.app.ui.screens.GuidedImageryScreen
import com.cerebrozen.app.ui.screens.HumanSupportScreen
import com.cerebrozen.app.ui.screens.InsightsScreen
import com.cerebrozen.app.ui.screens.JournalScreen
import com.cerebrozen.app.ui.screens.Onboarding
import com.cerebrozen.app.ui.screens.PatternGlowScreen
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
import com.cerebrozen.app.ui.screens.TippScreen
import com.cerebrozen.app.ui.screens.TodayScreen
import com.cerebrozen.app.ui.screens.ToolkitScreen
import com.cerebrozen.app.ui.screens.WindDownRitualScreen
import com.cerebrozen.app.ui.screens.YouScreen
import com.cerebrozen.app.ui.screens.ZenRipplesScreen
import com.cerebrozen.app.ui.screens.AppearanceScreen
import com.cerebrozen.app.ui.theme.AppTheme
import com.cerebrozen.app.ui.theme.NavPillBottom
import com.cerebrozen.app.ui.theme.NavPillTop
import com.cerebrozen.app.ui.theme.NavScrim
import com.cerebrozen.app.ui.theme.NavSelectedHi
import com.cerebrozen.app.ui.theme.NavSelectedLo
import com.cerebrozen.app.ui.theme.LavenderPillTop
import com.cerebrozen.app.ui.theme.LavenderPillFloor
import com.cerebrozen.app.ui.theme.Stroke
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.OnPrimary
import com.cerebrozen.app.ui.theme.VeilStrong
import com.cerebrozen.app.ui.theme.themeModeFromPref

// W24: the tabs wear the hand-drawn orb-family line icons (res/drawable/ic_tab_*)
// instead of stock Material glyphs — one consistent 2dp rounded-line set.
//
// W25 — the spec's five tabs (docs/REDESIGN_V2.md §3.1, owner ruling §6.1):
// **Today · Explore · Talk · Journal · You**. Two changes from the shipped set:
//
//  * Home is now **Today**. Only the label and the icon move; the route stays
//    `home` so every deeplink, saved back-stack entry, nudge target and test
//    that names it keeps working. The enum constant keeps its name for the same
//    reason — renaming it would churn ~30 call sites to no user-visible end.
//  * Sleep **leaves the tab bar** and Explore takes the slot. Sleep is now
//    reached from Explore, from Today's sleep entry points and from the sleep
//    quick tools; its route is unchanged, so nothing that pointed at it broke.
//    The demotion is a recorded owner decision, not a mechanical one — Sleep is
//    the evidenced flagship, and REDESIGN_V2 §6.1 says so out loud.
//
// Urgent support does NOT depend on this set and never did: it hangs off the
// You tab's Support card and Explore's support door, so it stays two taps from
// every tab (verified in NavigationChromeTest).
internal enum class Tab(val route: String, @androidx.annotation.StringRes val labelRes: Int, @androidx.annotation.DrawableRes val icon: Int) {
    Home("home", R.string.tab_today, R.drawable.ic_tab_today),
    Explore("explore", R.string.tab_explore, R.drawable.ic_tab_explore),
    Talk("talk", R.string.tab_talk, R.drawable.ic_tab_talk),
    Journal("journal", R.string.tab_journal, R.drawable.ic_tab_journal),
    You("you", R.string.tab_you, R.drawable.ic_tab_you),
}

internal fun shouldShowBottomBar(route: String?): Boolean =
    // talk/live + talk/chat are legacy aliases that render the Talk tab —
    // without them here a stale entry point showed Talk with no tab pill.
    // `sleep` is deliberately absent: it is a pushed screen now, and showing
    // the pill on a route no tab owns leaves five unlit tabs and no way to
    // tell where you are.
    route in setOf("home", "explore", "practice-library", "notice-change", "cbt", "body-scan-detail", "gratitude", "sleepinsights", "talk", "journal", "you", "talk/live", "talk/chat", "groundingintro", "checkin", "notifications", "insights", "trends", "patterns", "patterndetail", "dailyplan", "goals", "goaldetailcalmer", "goaldetailwind", "baseline", "reminders")

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
    val allowed = setOf(
        "home", "explore", "sleep", "talk", "journal", "journal/new", "you",
        "insights", "trends", "toolkit", "crisis", "safetyplan",
        "sounds", "sounds/mixer", "winddown", "plan", "goals", "programs",
        "breathe/reset", "breathe/box",
    )
    return resolved.takeIf { it in allowed }
}

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
            // The supplied phone reference places the floating capsule almost
            // on the lower edge. Scaffold already owns this bottom slot, so an
            // additional navigationBarsPadding lifted it much too high.
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
    val current = backStack?.destination?.route ?: Tab.Home.route
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
                    current == "groundingintro" || current == "checkin" || current == "notifications" || current == "insights" || current == "trends" || current == "patterns" || current == "patterndetail" || current == "dailyplan" || current == "goals" || current == "goaldetailcalmer" || current == "goaldetailwind" || current == "baseline" -> Tab.Home.route
                    // `sleep` is gone from this list: it left shouldShowBottomBar
                    // when it became a pushed screen, so this branch could never
                    // be reached — the bar is not drawn on that route at all.
                    current == "practice-library" || current == "notice-change" || current == "cbt" || current == "body-scan-detail" || current == "gratitude" || current == "sleepinsights" -> Tab.Explore.route
                    current == "reminders" -> Tab.You.route
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
            startDestination = Tab.Home.route,
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
                if (route in tabRoutes) {
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
            composable(Tab.Explore.route) { ExploreScreen(onOpen = open) }
            composable("practice-library") { PracticeLibraryScreen(onBack = back, onOpen = open) }
            composable("breathing-intro") {
                PracticeBreathingScreen(onBack = back, onUrgent = { open("crisis") }, onBegin = { open("breathe/reset") })
            }
            // Sleep is a pushed screen since the five-tab pass — it takes an
            // onBack so it carries a visible back door, not just the gesture.
            composable("sleep") { SleepScreen(onOpen = open, onBack = back) }
            composable(Tab.Talk.route) { TalkScreen(onOpen = open) }
            // The live/chat split belonged to the two-mode Talk screen. Talk is
            // one surface again — voice and typing live together, with the
            // composer pinned below the transcript — so both routes land on it
            // rather than dead-ending anything still pointing at them.
            composable("talk/live") { TalkScreen(onOpen = open) }
            composable("talk/chat") { TalkScreen(onOpen = open) }
            composable(Tab.Journal.route) { JournalScreen(onOpen = open) }
            // The Home check-in's "Say more" bridge lands in the composer, not the hub.
            composable("journal/new") { JournalScreen(startInEntry = true, onOpen = open, onExit = back) }
            composable(Tab.You.route) { YouScreen(onOpen = open) }
            composable("insights") { WeeklyInsightsScreen(onBack = back, onOpen = open) }
            composable("programs") { ProgramsScreen(onBack = back, onOpen = open) }
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
            composable("plan") { PlanScreen(onBack = back) }
            composable("search") { SearchScreen(onBack = back) }
            composable("patterns") { ReferencePatternsScreen(onBack = back, onOpen = open) }
            composable("patterndetail") { PatternDetailScreen(onBack = back, onOpen = open) }
            composable("sleepinsights") { ReferenceSleepInsightsScreen(onBack = back, onOpen = open) }
            composable("dailyplan") { ReferenceDailyPlanScreen(onBack = back, onOpen = open) }
            composable("trends") {
                ReferenceTrendsScreen(
                    onBack = back,
                    onReviewPatterns = { open("patterns") },
                    onUrgent = { open("crisis") },
                )
            }
            composable("safetyplan") { SafetyPlanScreen(onBack = back) }
            composable("goals") { ReferenceGoalsScreen(onBack = back, onOpen = open) }
            composable("goaldetailcalmer") { ReferenceGoalDetailScreen("A calmer evening", onBack = back, onOpen = open) }
            composable("goaldetailwind") { ReferenceGoalDetailScreen("Wind down before 10 PM", onBack = back, onOpen = open) }
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
            composable("tools") { ToolkitScreen(onOpen = open, onBack = back) }
            // The one parameterized breathe engine (box / two-minute reset).
            composable("breathe/box") { BreathLoopsScreen(onBack = back) }
            composable("guidedimagery") { GuidedImageryScreen(onBack = back) }
            composable("bodyscan") { PracticeBodyScanScreen(onBack = back, onUrgent = { open("crisis") }, onTranscript = { open("body-scan-detail") }) }
            composable("body-scan-detail") { BodyScanContentDetailScreen(onBack = back, onUrgent = { open("crisis") }, onBegin = { open("bodyscan") }) }
            composable("crisisgrounding") { CrisisGroundingScreen(onBack = back) { open("breathe/box") } }
            composable("insightreel") { InsightReelScreen(onBack = back) }
            composable("cbti") { CbtIOfflineScreen(onBack = back) }
            composable("mbct") { MbctOfflineScreen(onBack = back) }
            composable("breathe/reset") { BreatheScreen(BreathePreset.Reset, onBack = back) }
            // The two guided routines (web parity): the Sleep tab's wind-down
            // and the Toolkit's Settle visualization.
            composable("winddown") { WindDownRitualScreen(onBack = back) }
            composable("imagery") { GuidedImageryScreen(onOpen = open, onBack = back) }
            composable("ritual") { RitualBuilderScreen(onBack = back) }
            composable("bubblepop") { BubblePopScreen(onBack = back) }
            composable("patternglow") { PatternGlowScreen(onBack = back) }
            composable("ground") { GroundingScreen(onBack = back) }
            composable("zenripples") { ZenRipplesScreen(onBack = back) }
            composable("gratitude") { GratitudeReflectionScreen(onBack = back, onUrgent = { open("crisis") }) }
            composable("baseline") { ReferenceBaselineScreen(onBack = back, onOpen = open) }
            composable("breathing") { BreathingScreen(onBack = back) }
            composable("cbt") { UntangleThoughtScreen(onBack = back, onUrgent = { open("crisis") }) }
            composable("tipp") { PracticeTippScreen(onBack = back, onUrgent = { open("crisis") }, onTryStep = { open("notice-change") }) }
            composable("notice-change") { NoticeChangeScreen(onBack = back, onUrgent = { open("crisis") }) }
            composable("onegoodthing") { OneGoodThingScreen(onBack = back) }
            composable("intention") { IntentionScreen(onBack = back) }
            composable("crisis") { UrgentSupportScreen(onBack = back, onOpen = open) }
            composable("companion") { CompanionStyleScreen(onBack = back) }
            composable("language") { LanguageScreen(onBack = back) }
            composable("notifications") { NotificationInboxScreen(onBack = back, onOpen = open) }
            composable("appearance") { AppearanceScreen(onBack = back) }
            composable("reminders") { ReferenceRemindersScreen(onBack = back, onOpen = open) }
            composable("privacy") { PrivacyScreen(onBack = back) }
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
