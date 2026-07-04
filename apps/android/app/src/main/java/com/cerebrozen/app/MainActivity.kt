package com.cerebrozen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.CereBroApp
import com.cerebrozen.app.ui.theme.CereBroTheme

class MainActivity : ComponentActivity() {
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
