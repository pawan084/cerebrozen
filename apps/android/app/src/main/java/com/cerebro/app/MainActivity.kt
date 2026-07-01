package com.cerebro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cerebro.app.ui.CereBroApp
import com.cerebro.app.ui.theme.CereBroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CereBroTheme {
                CereBroApp()
            }
        }
    }
}
