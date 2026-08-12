package com.cerebrozen.app.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.notify.NotificationLog
import com.cerebrozen.app.notify.Push
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextSoft
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * TOD-06 — what CereBro has actually sent, and what it is going to send.
 *
 * Two sections because they answer two different questions, and the prototype
 * conflates them into one "Recent nudges" list where a *scheduled* row and a
 * *delivered* row look identical. "Is my reminder still on?" and "did the
 * 9:15 one fire?" are not the same question and must not share a card style.
 *
 * The empty state is the part that has to be careful. Remote nudges are inert
 * on any build without a `google-services.json` ([Push.available]), so an empty
 * list can mean "nothing has been sent to you" or "delivery is not switched on
 * in this build" — and telling a user the first when the second is true is the
 * kind of quiet lie this product is built not to tell. The screen distinguishes
 * them.
 */
@Composable
fun NotificationInboxScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(NotificationLog.all()) }
    val prefs = remember(context) {
        context.getSharedPreferences("cerebro", Context.MODE_PRIVATE)
    }
    val reminderOn = prefs.getBoolean("reminder_on", false)
    // Reminders.storedHour, not a second read with its own default. This line
    // was `getInt("reminder_hour", 20)` while Reminders.DEFAULT_HOUR is 9, so
    // before the user picked an hour the scheduler armed 9am and this screen
    // promised 8pm — the one screen whose job is saying when a nudge will fire.
    val reminderHour = com.cerebrozen.app.notify.Reminders.storedHour(context)
    val pushConfigured = remember(context) { Push.available(context) }

    PremiumSubPage(
        stringResource(R.string.inbox_eyebrow),
        stringResource(R.string.inbox_title),
        onBack,
    ) {
        // ── Scheduled ───────────────────────────────────────────────────────
        Text(
            stringResource(R.string.inbox_group_scheduled),
            style = MaterialTheme.typography.labelSmall, color = Periwinkle,
        )
        if (reminderOn) {
            SectionCard(onClick = { onOpen("reminders") }) {
                Text(
                    stringResource(R.string.inbox_scheduled_daily),
                    style = MaterialTheme.typography.titleMedium, color = TextSoft,
                )
                Text(
                    stringResource(R.string.inbox_scheduled_daily_at, formatHour(reminderHour)),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
            }
        } else {
            SectionCard(onClick = { onOpen("reminders") }, quiet = true) {
                Text(
                    stringResource(R.string.inbox_scheduled_none),
                    style = MaterialTheme.typography.titleMedium, color = TextSoft,
                )
                Text(
                    stringResource(R.string.inbox_scheduled_none_hint),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
            }
        }

        // ── Delivered ───────────────────────────────────────────────────────
        Text(
            stringResource(R.string.inbox_group_delivered),
            style = MaterialTheme.typography.labelSmall, color = Periwinkle,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (entries.isEmpty()) {
            SectionCard(quiet = true) {
                Text(
                    stringResource(R.string.inbox_empty_title),
                    style = MaterialTheme.typography.titleMedium, color = TextSoft,
                )
                Text(
                    // The honest distinction: nothing has arrived yet, versus
                    // remote delivery is not configured on this build at all.
                    stringResource(
                        if (pushConfigured) R.string.inbox_empty_body
                        else R.string.inbox_empty_body_push_off,
                    ),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
            }
        } else {
            entries.forEach { entry ->
                SectionCard {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    if (entry.body.isNotBlank()) {
                        Text(entry.body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                    Text(
                        relativeDelivery(entry.at),
                        style = MaterialTheme.typography.bodySmall, color = TextMuted2,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // "Open" only when the nudge actually has somewhere to
                        // go — a dead Open button is worse than none.
                        entry.route?.let { route ->
                            TextButton(onClick = { onOpen(route) }) {
                                Text(stringResource(R.string.inbox_open), color = Periwinkle)
                            }
                        }
                        TextButton(onClick = { entries = NotificationLog.remove(entry.at) }) {
                            Text(stringResource(R.string.inbox_dismiss), color = TextMuted)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { NotificationLog.clear(); entries = emptyList() }) {
                    Text(stringResource(R.string.inbox_clear_all), color = TextMuted)
                }
            }
        }

        Text(
            stringResource(R.string.inbox_local_note),
            style = MaterialTheme.typography.bodySmall, color = TextMuted2,
        )
    }
}

/** A 24h hour as a locale-formatted clock time (20 → "8:00 PM" / "20:00"). */
internal fun formatHour(hour: Int): String = runCatching {
    java.time.LocalTime.of(hour.coerceIn(0, 23), 0)
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
}.getOrDefault("$hour:00")

/** Delivery instants render as a short absolute date-time rather than "2 days
 * ago": the question this screen answers is "did it fire, and when", and a
 * relative label loses the "when" as soon as it passes a day. */
internal fun relativeDelivery(iso: String): String = runCatching {
    OffsetDateTime.parse(iso)
        .format(DateTimeFormatter.ofPattern("d MMM · h:mm a", Locale.getDefault()))
}.getOrDefault(iso)
