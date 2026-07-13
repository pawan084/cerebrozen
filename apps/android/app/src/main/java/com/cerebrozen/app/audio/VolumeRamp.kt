package com.cerebrozen.app.audio

import android.os.Handler
import android.os.Looper

/**
 * W27 §1 (Calm study): one shared Handler-based volume ramp for every audio
 * surface — extracted from the stepping pattern `ToolAmbience` proved out.
 * Play starts silent and rises to its target, pause tails out before the
 * player actually pauses, and the Player↔Mixer exclusivity handoff lets the
 * dying engine fade under the arriving one instead of hard-cutting.
 *
 * Owns its own [Handler] on the main looper so cancelling a ramp never
 * disturbs a service's other posted work (sleep-timer ticks, fades). Values
 * are always coerced to 0..1 before [onStep] sees them.
 */
internal class VolumeRamp(private val handler: Handler = Handler(Looper.getMainLooper())) {

    companion object {
        /** The Calm-study crossfade: ~600ms in 12 gentle steps. */
        const val DEFAULT_MS = 600L
        private const val STEPS = 12
    }

    /**
     * Step from [from] to [to] over [durationMs], calling [onStep] with each
     * intermediate value and [onDone] once the target has been applied. A new
     * ramp replaces any ramp still in flight (last intent wins).
     */
    fun ramp(
        from: Float,
        to: Float,
        durationMs: Long = DEFAULT_MS,
        onStep: (Float) -> Unit,
        onDone: (() -> Unit)? = null,
    ) {
        cancel()
        val stepMs = (durationMs / STEPS).coerceAtLeast(1L)
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step++
                val v = from + (to - from) * (step.toFloat() / STEPS)
                onStep(v.coerceIn(0f, 1f))
                if (step < STEPS) handler.postDelayed(this, stepMs) else onDone?.invoke()
            }
        }
        handler.postDelayed(runnable, stepMs)
    }

    /** Drop any ramp in flight (its onDone never fires). */
    fun cancel() {
        handler.removeCallbacksAndMessages(null)
    }
}
