package com.cerebrozen.app.net

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background drain for the [Outbox] — the half of "saved, will sync" that was
 * missing (WC-190).
 *
 * The queue itself has always survived process death; the *drain* did not. It
 * ran only while the app was open (launch, a successful read, pull-to-refresh),
 * so a check-in logged on the metro and backgrounded synced whenever the person
 * next happened to open the app — hours, sometimes days. The promise printed
 * under the entry is "it will send itself when you're back", and this makes
 * that true: a [NetworkType.CONNECTED]-constrained one-shot that WorkManager
 * runs when connectivity actually returns, app open or not.
 *
 * Why WorkManager and not a connectivity receiver or a foreground service:
 * it is the platform's own answer to Doze and App Standby — the drain rides
 * maintenance windows instead of fighting them (WC-191), survives reboots,
 * and needs no battery-exemption prompt. A wellness app asking to be excluded
 * from battery optimisation to sync a mood log would have its priorities
 * exactly backwards.
 *
 * One platform boundary, found on the CPH2681 (2026-08-24): after a
 * FORCE-stop (settings "Force stop", or `adb shell am force-stop`) the app is
 * in stopped state and Android freezes its scheduled jobs until the next
 * manual launch — no worker can outlive that, by design. The kill that
 * matters — the system reclaiming a backgrounded process — was proven live:
 * process count 0, connectivity restored, and WorkManager spawned the process
 * itself and drained the queue with nobody touching the phone.
 *
 * Scheduling rules:
 *  - [ExistingWorkPolicy.KEEP]: ten offline check-ins are one drain, not ten.
 *  - Exponential backoff from 30s: a flapping network (WC-189) re-runs the
 *    drain a few times cheaply, then backs off instead of hammering.
 *  - The worker never touches item semantics — ordering, idempotency keys,
 *    what is retryable — all of that stays in [Outbox.drain], where the JVM
 *    tests pin it. This class only decides WHEN, never WHAT.
 */
object OutboxSync {
    private const val WORK_NAME = "outbox-sync"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

class OutboxSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // WorkManager may start this in a fresh process where MainActivity's
        // onCreate never ran; Session.init is idempotent and cheap when it did.
        Session.init(applicationContext)
        if (!Session.signedIn) return Result.success()   // nothing can be sent

        val drained = runCatching { Outbox.drain() }.getOrNull()
            ?: return Result.retry()                     // drain itself blew up

        return when {
            drained.remaining == 0 -> Result.success()
            // Still stuck after several connected attempts: stop burning the
            // maintenance windows. The queue is durable and every in-app drain
            // trigger still fires — giving up HERE loses nothing.
            runAttemptCount >= 5 -> Result.success()
            else -> Result.retry()
        }
    }
}
