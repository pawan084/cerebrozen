package com.cerebrozen.app

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.CereBroApp
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.CereBroTheme
import kotlinx.coroutines.launch

// FragmentActivity (still a ComponentActivity) so androidx.biometric can
// attach its prompt — needed by the journal lock.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The OS-drawn splash (branded orb on night) owns the launch moment — installed
        // BEFORE super.onCreate so there is no unbranded first frame. See docs/SPLASH_SPEC.md.
        val splash = installSplashScreen()
        // Transparent system bars so the app's gradient backgrounds run edge to edge.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        Haptics.init(applicationContext)

        // Hold the branded splash until the warm-path readiness signal (session + plan) —
        // real readiness, never an arbitrary timer. warmBoot() always flips bootReady, so a
        // slow/absent network can never trap the launch. Skipped on warm start (a config
        // change already has bootReady=true from the prior run, so this is a no-op there).
        if (!Session.bootReady) lifecycleScope.launch { Session.warmBoot() }
        splash.setKeepOnScreenCondition { !Session.bootReady }
        // Hand the branded icon off with a soft fade+lift instead of a hard cut, so the
        // system splash and the app's aurora arrival read as one motion.
        splash.setOnExitAnimationListener { provider ->
            val icon = provider.view
            ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f).apply { duration = 220 }.start()
            ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, 0f, -icon.height * 0.06f).apply {
                duration = 220
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        provider.remove()
                    }
                })
                start()
            }
        }

        setContent {
            CereBroTheme {
                CereBroApp()
            }
        }
    }
}
