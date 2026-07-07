package com.cerebrozen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.screens.AccountDeletionScreen
import com.cerebrozen.app.ui.screens.BaselineScreen
import com.cerebrozen.app.ui.screens.BubblePopScreen
import com.cerebrozen.app.ui.screens.BubbleWrapScreen
import com.cerebrozen.app.ui.screens.CompanionStyleScreen
import com.cerebrozen.app.ui.screens.CrisisRegionScreen
import com.cerebrozen.app.ui.screens.CrisisScreen
import com.cerebrozen.app.ui.screens.DataExportScreen
import com.cerebrozen.app.ui.screens.GamesScreen
import com.cerebrozen.app.ui.screens.GratitudeGardenScreen
import com.cerebrozen.app.ui.screens.HumanSupportScreen
import com.cerebrozen.app.ui.screens.InsightsScreen
import com.cerebrozen.app.ui.screens.JournalScreen
import com.cerebrozen.app.ui.screens.MemoryMatchScreen
import com.cerebrozen.app.ui.screens.Onboarding
import com.cerebrozen.app.ui.screens.PatternGlowScreen
import com.cerebrozen.app.ui.screens.PremiumScreen
import com.cerebrozen.app.ui.screens.PrivacyPolicyScreen
import com.cerebrozen.app.ui.screens.PrivacyScreen
import com.cerebrozen.app.ui.screens.ProgramsScreen
import com.cerebrozen.app.ui.screens.RemindersScreen
import com.cerebrozen.app.ui.screens.SleepScreen
import com.cerebrozen.app.ui.screens.SoundsScreen
import com.cerebrozen.app.ui.screens.TalkScreen
import com.cerebrozen.app.ui.screens.TodayScreen
import com.cerebrozen.app.ui.screens.YouScreen
import com.cerebrozen.app.ui.screens.ZenRipplesScreen
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.NightMid
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted2

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Sleep("sleep", "Sleep", Icons.Filled.Bedtime),
    Talk("talk", "Talk", Icons.Filled.Mic),
    Journal("journal", "Journal", Icons.Filled.Book),
    You("you", "You", Icons.Filled.Person),
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

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Column {
                // Hairline glass edge so the bar reads as a distinct surface.
                HorizontalDivider(thickness = 1.dp, color = LineStroke)
                NavigationBar(containerColor = NightMid, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                if (current != tab.route) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Periwinkle,
                                selectedTextColor = Periwinkle,
                                unselectedIconColor = TextMuted2,
                                unselectedTextColor = TextMuted2,
                                indicatorColor = Periwinkle.copy(alpha = 0.18f),
                            ),
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
            composable(Tab.Sleep.route) { SleepScreen() }
            composable(Tab.Talk.route) { TalkScreen(onOpen = open) }
            composable(Tab.Journal.route) { JournalScreen() }
            composable(Tab.You.route) { YouScreen(onOpen = open) }
            composable("insights") { InsightsScreen(onBack = back) }
            composable("programs") { ProgramsScreen(onBack = back) }
            composable("sounds") { SoundsScreen(onBack = back) }
            composable("games") { GamesScreen(onOpen = open, onBack = back) }
            composable("bubblepop") { BubblePopScreen(onBack = back) }
            composable("bubblewrap") { BubbleWrapScreen(onBack = back) }
            composable("memorymatch") { MemoryMatchScreen(onBack = back) }
            composable("patternglow") { PatternGlowScreen(onBack = back) }
            composable("zenripples") { ZenRipplesScreen(onBack = back) }
            composable("gratitude") { GratitudeGardenScreen(onBack = back) }
            composable("baseline") { BaselineScreen(onBack = back) }
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
