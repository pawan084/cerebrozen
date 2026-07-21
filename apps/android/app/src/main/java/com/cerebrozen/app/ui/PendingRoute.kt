package com.cerebrozen.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A route requested from OUTSIDE Compose — a launcher app-shortcut ("Talk it through" /
 * "Breathe", HOME_SPEC #23) or a tapped notification (the daily reminder, HOME_SPEC #24) —
 * both deliver as an Intent extra on [com.cerebrozen.app.MainActivity], which has no NavHost
 * of its own to navigate. MainActivity stashes the requested route in its OWN instance of
 * this holder (cold start, before `setContent`, AND `onNewIntent` while already running);
 * [CereBroApp]'s NavHost observes it and clears it after navigating, so it never re-fires on
 * a later recomposition.
 *
 * MUST be Compose-observable ([mutableStateOf]), not a plain `var` — device-verified
 * 2026-07-20 that a plain var silently did nothing for a WARM re-tap (the app already
 * running): `onNewIntent` set the value, but nothing told the already-composed NavHost's
 * `LaunchedEffect` to look at it again, so only a cold start ever actually navigated.
 *
 * MUST be PER-ACTIVITY, not an `object` singleton — device-verified 2026-07-21, and it only
 * reproduces with the flags the launcher really sends. A shortcut carries
 * `FLAG_ACTIVITY_NEW_TASK | CLEAR_TASK | CLEAR_TOP` (`adb shell dumpsys shortcut` prints
 * `flg=0x1000c000`), and CLEAR_TASK **recreates** the activity instead of delivering
 * `onNewIntent`. With a process-global holder the sequence was:
 *
 *   1. the new `MainActivity.onCreate` sets the shared value to "coach";
 *   2. the OUTGOING activity's composition is still alive, observes the change, navigates
 *      its own dying NavHost, and clears the value;
 *   3. the new activity composes, finds `null`, and lands on Today.
 *
 * So the app opened Home while the user was looking at the tile they had just tapped. A COLD
 * start always worked — there is no earlier composition to steal from — which is exactly why
 * this survived the first device pass and why no unit test can see it: the race needs two
 * live activity instances.
 *
 * One holder per activity instance removes the shared thing to race over. It is deliberately
 * a `class`, so it cannot quietly regress to a singleton.
 */
class PendingRoute {
    var value: String? by mutableStateOf(null)
}
