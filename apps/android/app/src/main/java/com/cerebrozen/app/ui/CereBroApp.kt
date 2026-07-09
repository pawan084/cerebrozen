package com.cerebro.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cerebro.app.net.Session
import com.cerebro.app.ui.screens.AccountDeletionScreen
import com.cerebro.app.ui.screens.BaselineScreen
import com.cerebro.app.ui.screens.BubblePopScreen
import com.cerebro.app.ui.screens.BubbleWrapScreen
import com.cerebro.app.ui.screens.CbtReframeScreen
import com.cerebro.app.ui.screens.CompanionStyleScreen
import com.cerebro.app.ui.screens.CrisisRegionScreen
import com.cerebro.app.ui.screens.CrisisScreen
import com.cerebro.app.ui.screens.DataExportScreen
import com.cerebro.app.ui.screens.GamesScreen
import com.cerebro.app.ui.screens.GratitudeGardenScreen
import com.cerebro.app.ui.screens.HumanSupportScreen
import com.cerebro.app.ui.screens.InsightsScreen
import com.cerebro.app.ui.screens.JournalScreen
import com.cerebro.app.ui.screens.IntentionScreen
import com.cerebro.app.ui.screens.MemoryMatchScreen
import com.cerebro.app.ui.screens.Onboarding
import com.cerebro.app.ui.screens.OneGoodThingScreen
import com.cerebro.app.ui.screens.PatternGlowScreen
import com.cerebro.app.ui.screens.PatternScreen
import com.cerebro.app.ui.screens.PlanScreen
import com.cerebro.app.ui.screens.PlayerScreen
import com.cerebro.app.ui.screens.SearchScreen
import com.cerebro.app.ui.screens.PremiumScreen
import com.cerebro.app.ui.screens.PrivacyPolicyScreen
import com.cerebro.app.ui.screens.PrivacyScreen
import com.cerebro.app.ui.screens.ProgramsScreen
import com.cerebro.app.ui.screens.RemindersScreen
import com.cerebro.app.ui.screens.SleepScreen
import com.cerebro.app.ui.screens.SoundsScreen
import com.cerebro.app.ui.screens.TalkScreen
import com.cerebro.app.ui.screens.TippScreen
import com.cerebro.app.ui.screens.TodayScreen
import com.cerebro.app.ui.screens.ToolsScreen
import com.cerebro.app.ui.screens.YouScreen
import com.cerebro.app.ui.screens.ZenRipplesScreen
import com.cerebro.app.ui.theme.LineStroke
import com.cerebro.app.ui.theme.NightMid
import com.cerebro.app.ui.theme.Night
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted2

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Sleep("sleep", "Sleep", Icons.Filled.Bedtime),
    Talk("talk", "Talk", Icons.Filled.Mic),
    Journal("journal", "Journal", Icons.Filled.Book),
    You("you", "You", Icons.Filled.Person),
}

@Composable
private fun BottomTabItem(
    tab: Tab,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = Color.White
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) {
                    Brush.radialGradient(
                        listOf(Periwinkle.copy(alpha = 0.72f), Periwinkle.copy(alpha = 0.18f)),
                    )
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
        Box(
            Modifier
                .size(if (compact) 26.dp else 29.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(if (compact) 15.dp else 17.dp))
        }
        Text(
            tab.label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun CereBroApp() {
    // A brief branded splash on cold launch.
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1100); showSplash = false }
    if (showSplash) { Splash(); return }

    // Signed-out: the whole app is the onboarding/auth flow (live backend session,
    // same account as iOS/web). Session.signedIn is Compose-observable.
    if (!Session.signedIn) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NightMid, Night))),
        ) { Onboarding() }
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Tab.Home.route
    val haptics = LocalHapticFeedback.current
    val compactNav = LocalConfiguration.current.screenWidthDp < 380

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF0B061E).copy(alpha = 0.96f)),
                        ),
                    )
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
                                listOf(Color(0xFF504184).copy(alpha = 0.96f), Color(0xFF292052).copy(alpha = 0.98f)),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(24.dp))
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
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NightMid, Night)))
                .padding(padding),
            enterTransition = { fadeIn(tween(240)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(240)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            val open: (String) -> Unit = { route -> navController.navigate(route) }
            val back: () -> Unit = { navController.popBackStack() }
            composable(Tab.Home.route) { TodayScreen(onOpen = open) }
            composable(Tab.Sleep.route) { SleepScreen(onOpen = open) }
            composable(Tab.Talk.route) { TalkScreen(onOpen = open) }
            composable(Tab.Journal.route) { JournalScreen() }
            composable(Tab.You.route) { YouScreen(onOpen = open) }
            composable("insights") { InsightsScreen(onBack = back) }
            composable("programs") { ProgramsScreen(onBack = back) }
            composable("sounds") { SoundsScreen(onBack = back, onOpen = open) }
            composable("player") { PlayerScreen(onBack = back) }
            composable("plan") { PlanScreen(onBack = back) }
            composable("search") { SearchScreen(onBack = back) }
            composable("patterns") { PatternScreen(onBack = back) }
            composable("games") { GamesScreen(onOpen = open, onBack = back) }
            composable("bubblepop") { BubblePopScreen(onBack = back) }
            composable("bubblewrap") { BubbleWrapScreen(onBack = back) }
            composable("memorymatch") { MemoryMatchScreen(onBack = back) }
            composable("patternglow") { PatternGlowScreen(onBack = back) }
            composable("zenripples") { ZenRipplesScreen(onBack = back) }
            composable("gratitude") { GratitudeGardenScreen(onBack = back) }
            composable("baseline") { BaselineScreen(onBack = back) }
            composable("tools") { ToolsScreen(onOpen = open, onBack = back) }
            composable("cbt") { CbtReframeScreen(onBack = back) }
            composable("onegoodthing") { OneGoodThingScreen(onBack = back) }
            composable("intention") { IntentionScreen(onBack = back) }
            composable("tipp") { TippScreen(onBack = back) }
            composable("crisis") { CrisisScreen(onBack = back) }
            composable("companion") { CompanionStyleScreen(onBack = back) }
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
}
