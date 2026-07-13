package com.cerebrozen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.cerebrozen.app.ui.screens.AuroraBackground
import com.cerebrozen.app.ui.screens.SceneVideo
import com.cerebrozen.app.ui.screens.BreathePreset
import com.cerebrozen.app.ui.screens.BreatheScreen
import com.cerebrozen.app.ui.screens.BreathingScreen
import com.cerebrozen.app.ui.screens.BubblePopScreen
import com.cerebrozen.app.ui.screens.Celebration
import com.cerebrozen.app.ui.screens.Celebrations
import com.cerebrozen.app.ui.screens.CbtReframeScreen
import com.cerebrozen.app.ui.screens.CompanionStyleScreen
import com.cerebrozen.app.ui.screens.CrisisRegionScreen
import com.cerebrozen.app.ui.screens.CrisisScreen
import com.cerebrozen.app.ui.screens.DataExportScreen
import com.cerebrozen.app.ui.screens.GratitudeGardenScreen
import com.cerebrozen.app.ui.screens.HumanSupportScreen
import com.cerebrozen.app.ui.screens.InsightsScreen
import com.cerebrozen.app.ui.screens.JournalScreen
import com.cerebrozen.app.ui.screens.Onboarding
import com.cerebrozen.app.ui.screens.PatternGlowScreen
import com.cerebrozen.app.ui.screens.PatternScreen
import com.cerebrozen.app.ui.screens.PlanScreen
import com.cerebrozen.app.ui.screens.PlayerScreen
import com.cerebrozen.app.ui.screens.SearchScreen
import com.cerebrozen.app.ui.screens.PremiumScreen
import com.cerebrozen.app.ui.screens.PrivacyPolicyScreen
import com.cerebrozen.app.ui.screens.PrivacyScreen
import com.cerebrozen.app.ui.screens.ProgramsScreen
import com.cerebrozen.app.ui.screens.RemindersScreen
import com.cerebrozen.app.ui.screens.SleepScreen
import com.cerebrozen.app.ui.screens.SoundsScreen
import com.cerebrozen.app.ui.screens.TalkScreen
import com.cerebrozen.app.ui.screens.TalkMode
import com.cerebrozen.app.ui.screens.TippScreen
import com.cerebrozen.app.ui.screens.TodayScreen
import com.cerebrozen.app.ui.screens.ToolkitScreen
import com.cerebrozen.app.ui.screens.YouScreen
import com.cerebrozen.app.ui.screens.ZenRipplesScreen
import com.cerebrozen.app.ui.screens.AppearanceScreen
import com.cerebrozen.app.ui.theme.AppTheme
import com.cerebrozen.app.ui.theme.NavPillBottom
import com.cerebrozen.app.ui.theme.NavPillTop
import com.cerebrozen.app.ui.theme.NavScrim
import com.cerebrozen.app.ui.theme.NavSelectedHi
import com.cerebrozen.app.ui.theme.NavSelectedLo
import com.cerebrozen.app.ui.theme.Stroke
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.VeilStrong
import com.cerebrozen.app.ui.theme.themeModeFromPref

// W24: the tabs wear the hand-drawn orb-family line icons (res/drawable/ic_tab_*)
// instead of stock Material glyphs — one consistent 2dp rounded-line set.
private enum class Tab(val route: String, @androidx.annotation.StringRes val labelRes: Int, @androidx.annotation.DrawableRes val icon: Int) {
    Home("home", R.string.tab_home, R.drawable.ic_tab_home),
    Sleep("sleep", R.string.tab_sleep, R.drawable.ic_tab_sleep),
    Talk("talk", R.string.tab_talk, R.drawable.ic_tab_talk),
    Journal("journal", R.string.tab_journal, R.drawable.ic_tab_journal),
    You("you", R.string.tab_you, R.drawable.ic_tab_you),
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
    val tint = if (selected) TextPrimary else TextMuted2
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
                    // NavSelectedHi/Lo = the Periwinkle wash, tuned per theme so the
                    // selected label keeps AA contrast on both pills (Color.kt).
                    Brush.radialGradient(listOf(NavSelectedHi, NavSelectedLo))
                } else {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .border(
                1.dp,
                if (selected) Color.White.copy(alpha = 0.20f) else Color.Transparent,
                RoundedCornerShape(20.dp),
            )
            .clickable { onClick() }
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
                contentDescription = label,
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
 * resolved theme only (Appearance choice / system dark), deliberately ignoring
 * the Sleep tab's forceNight flips, which keep their existing nav cross-fade.
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
 * light icons over Night, dark icons over Dawn. */
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
    remember { AppTheme.mode = themeModeFromPref(Session.prefGet("theme_mode")); true }
    SyncSystemBarIcons()

    // A brief branded splash on cold launch — always Night (brand moment).
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1100); showSplash = false }
    if (showSplash) {
        AppTheme.forceNight = true
        Splash()
        return
    }

    // Signed-out: the whole app is the onboarding/auth flow (live backend session,
    // same account as iOS/web). Session.signedIn is Compose-observable. The funnel's
    // bespoke night art doesn't theme, so it is always Night.
    if (!Session.signedIn) {
        AppTheme.forceNight = true
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

    // Resolve the server sound/video catalogue once per launch, then pull the
    // one-shot assets onto disk so taps fire with no network in the path. Both
    // steps are best-effort: with no catalogue (offline, first run, server down)
    // every sound falls back to its synthesized tone or bundled loop, so the app
    // is fully audible either way — this only ever upgrades what's already there.
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        runCatching { MediaCatalog.load(Api.mediaCatalog(), BuildConfig.API_BASE_URL) }
        runCatching { Sfx.warm(appContext) }
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Tab.Home.route
    // Sleep contexts always keep the night palette (REDESIGN §4.1).
    AppTheme.forceNight = current == Tab.Sleep.route || current.startsWith("sounds")
    val haptics = LocalHapticFeedback.current
    val compactNav = LocalConfiguration.current.screenWidthDp < 380
    // Aurora hue shifts by section (sleep = violet, talk = cyan, else lavender).
    // E6: the accent cross-fades between tabs instead of snapping; Reduce Motion
    // keeps the honest instant snap.
    val reduceMotion = com.cerebrozen.app.ui.screens.rememberReduceMotion()
    val auroraAccent by animateColorAsState(
        targetValue = when (current) {
            Tab.Sleep.route, "sounds", "sounds/mixer" -> com.cerebrozen.app.ui.theme.Accent.sleep
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
        // Sleep tab only. The pushed "sounds"/mixer screens are built on
        // PremiumFrame, which paints its own opaque plate — a scene behind those
        // would decode and then be covered, burning a video decoder to render
        // nothing. Restrict it to the surface where it actually shows.
        current == Tab.Sleep.route -> MediaCatalog.urlFor(MediaCatalog.Keys.SCENE_NIGHT_LAKE)
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
            if (Tab.entries.any { it.route == current }) {
            // A floating lavender pill over a dark scrim — the tabs read as a lifted
            // capsule rather than a flat system bar.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, NavScrim.copy(alpha = 0.96f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 13.dp, vertical = 4.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(if (compactNav) 72.dp else 78.dp)
                        .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x66000000), spotColor = Color(0x66000000))
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(NavPillTop.copy(alpha = 0.96f), NavPillBottom.copy(alpha = 0.98f)),
                            ),
                        )
                        .border(1.dp, Stroke.navPill, RoundedCornerShape(24.dp))
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tab.entries.forEach { tab ->
                        BottomTabItem(
                            tab = tab,
                            selected = current == tab.route,
                            compact = compactNav,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (current != tab.route) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
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
            enterTransition = { fadeIn(tween(280)) + scaleIn(initialScale = 0.98f, animationSpec = tween(280)) },
            exitTransition = { fadeOut(tween(170)) + scaleOut(targetScale = 1.02f, animationSpec = tween(170)) },
            popEnterTransition = { fadeIn(tween(280)) + scaleIn(initialScale = 1.02f, animationSpec = tween(280)) },
            popExitTransition = { fadeOut(tween(170)) + scaleOut(targetScale = 0.98f, animationSpec = tween(170)) },
        ) {
            val open: (String) -> Unit = { route -> navController.navigate(route) }
            val back: () -> Unit = { navController.popBackStack() }
            composable(Tab.Home.route) { TodayScreen(onOpen = open) }
            composable(Tab.Sleep.route) { SleepScreen(onOpen = open) }
            composable(Tab.Talk.route) { TalkScreen(onOpen = open) }
            composable("talk/live") {
                TalkScreen(
                    onOpen = open,
                    mode = TalkMode.Live,
                    onBack = back,
                    onChat = {
                        navController.navigate("talk/chat") {
                            popUpTo("talk/live") { inclusive = true }
                        }
                    },
                )
            }
            composable("talk/chat") {
                TalkScreen(onOpen = open, mode = TalkMode.Chat, onBack = back)
            }
            composable(Tab.Journal.route) { JournalScreen() }
            composable(Tab.You.route) { YouScreen(onOpen = open) }
            composable("insights") { InsightsScreen(onBack = back, onOpen = open) }
            composable("programs") { ProgramsScreen(onBack = back) }
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
            ) { PlayerScreen(onBack = back) }
            composable("plan") { PlanScreen(onBack = back) }
            composable("search") { SearchScreen(onBack = back) }
            composable("patterns") { PatternScreen(onBack = back) }
            // Toolkit is the one activities hub (games + tools merged). The old
            // `games` and `tools` routes stay as aliases so Oracle widgets, plan
            // steps and saved deep-links keep landing somewhere real.
            composable("toolkit") { ToolkitScreen(onOpen = open, onBack = back) }
            composable("games") { ToolkitScreen(onOpen = open, onBack = back) }
            composable("tools") { ToolkitScreen(onOpen = open, onBack = back) }
            // The one parameterized breathe engine (box / two-minute reset).
            composable("breathe/box") { BreatheScreen(BreathePreset.Box, onBack = back) }
            composable("breathe/reset") { BreatheScreen(BreathePreset.Reset, onBack = back) }
            composable("bubblepop") { BubblePopScreen(onBack = back) }
            composable("patternglow") { PatternGlowScreen(onBack = back) }
            composable("zenripples") { ZenRipplesScreen(onBack = back) }
            composable("gratitude") { GratitudeGardenScreen(onBack = back) }
            composable("baseline") { BaselineScreen(onBack = back) }
            composable("breathing") { BreathingScreen(onBack = back) }
            composable("cbt") { CbtReframeScreen(onBack = back) }
            composable("tipp") { TippScreen(onBack = back) }
            composable("crisis") { CrisisScreen(onBack = back) }
            composable("companion") { CompanionStyleScreen(onBack = back) }
            composable("appearance") { AppearanceScreen(onBack = back) }
            composable("reminders") { RemindersScreen(onBack = back) }
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
