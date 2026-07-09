package com.cerebro.app

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.cerebro.app.net.Session
import com.cerebro.app.ui.CereBroApp
import com.cerebro.app.ui.theme.CereBroTheme

// FragmentActivity (still a ComponentActivity) so androidx.biometric can
// attach its prompt — needed by the journal lock.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        setContent {
            CereBroTheme {
                CereBroApp()
            }
        }
    }
}
