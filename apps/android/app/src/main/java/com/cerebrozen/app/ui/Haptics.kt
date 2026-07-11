package com.cerebrozen.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * A small, calm haptic vocabulary — the Android mirror of the iOS `Haptics` enum.
 *
 * Backed by the platform [Vibrator] so we get intensity and multi-pulse patterns
 * (Compose's LocalHapticFeedback only exposes a couple of constants). Every call
 * is a no-op until [init] runs and on devices without a vibrator, so callers can
 * fire freely. Kept deliberately gentle — this is a wellness app, not a game.
 */
object Haptics {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun oneShot(ms: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255))) }
    }

    private fun predefined(effect: Int, fallbackMs: Long, fallbackAmp: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { v.vibrate(VibrationEffect.createPredefined(effect)) }
        } else {
            oneShot(fallbackMs, fallbackAmp)
        }
    }

    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1)) }
    }

    /** A whisper of feedback for a press — [intensity] 0–1 (the calm workhorse). */
    fun soft(intensity: Float = 0.5f) = oneShot(16, (intensity.coerceIn(0.1f, 1f) * 200).toInt())

    /** A crisp tick for a selection / toggle. */
    fun selection() = predefined(VibrationEffect.EFFECT_TICK, fallbackMs = 12, fallbackAmp = 120)

    /** A firmer click for a primary tap. */
    fun tap() = predefined(VibrationEffect.EFFECT_CLICK, fallbackMs = 18, fallbackAmp = 160)

    /** Two gentle pulses — a felt reward for a completed action. */
    fun success() = waveform(longArrayOf(0, 16, 80, 26), intArrayOf(0, 150, 0, 210))

    /** A single stronger pulse to flag a problem. */
    fun warning() = oneShot(38, 200)
}
