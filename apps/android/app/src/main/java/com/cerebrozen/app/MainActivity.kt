package com.cerebrozen.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.CereBroApp
import com.cerebrozen.app.ui.theme.CereBroTheme

// FragmentActivity (still a ComponentActivity) so androidx.biometric can
// attach its prompt — needed by the journal lock.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        setContent {
            CereBroTheme {
                CereBroApp()
            }
        }
    }
}
