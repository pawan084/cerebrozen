package com.cerebrozen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.screens.AuthScreen
import com.cerebrozen.app.ui.screens.JournalScreen
import com.cerebrozen.app.ui.screens.SleepScreen
import com.cerebrozen.app.ui.screens.TalkScreen
import com.cerebrozen.app.ui.screens.TodayScreen
import com.cerebrozen.app.ui.screens.YouScreen
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
    // Signed-out: the whole app is the auth screen (live backend session,
    // same account as iOS/web). Session.signedIn is Compose-observable.
    if (!Session.signedIn) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(NightMid, Night))),
        ) { AuthScreen() }
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Tab.Home.route

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = NightMid) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
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
                            indicatorColor = Color.Transparent,
                        ),
                    )
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
        ) {
            composable(Tab.Home.route) { TodayScreen() }
            composable(Tab.Sleep.route) { SleepScreen() }
            composable(Tab.Talk.route) { TalkScreen() }
            composable(Tab.Journal.route) { JournalScreen() }
            composable(Tab.You.route) { YouScreen() }
        }
    }
}
