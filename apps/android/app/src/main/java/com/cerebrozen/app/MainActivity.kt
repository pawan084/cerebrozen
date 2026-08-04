package com.cerebrozen.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.CereBroApp
import com.cerebrozen.app.ui.DeeplinkBus
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.CereBroTheme

// FragmentActivity (still a ComponentActivity) so androidx.biometric can
// attach its prompt — needed by the journal lock.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent system bars so the app's gradient backgrounds run edge to edge.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        Haptics.init(applicationContext)
        // The nudge's promise: Push.kt attaches the deeplink to this intent;
        // the NavHost consumes it from the bus once signed in.
        DeeplinkBus.offer(intent?.dataString)
        setContent {
            CereBroTheme {
                CereBroApp()
            }
        }
    }

    // CLEAR_TOP|SINGLE_TOP notifications reuse the live activity — without
    // this a nudge tapped while the app was open navigated nowhere.
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        DeeplinkBus.offer(intent.dataString)
    }
}
