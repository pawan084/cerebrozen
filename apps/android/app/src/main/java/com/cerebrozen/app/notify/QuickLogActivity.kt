package com.cerebrozen.app.notify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.MainActivity
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.screens.MOODS
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.CereBroTheme
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import androidx.compose.foundation.border
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * V3-e: the quick-log popup (owner-approved companion-first reference).
 *
 * A notification's "Check in" action opens this small translucent dialog over
 * whatever is on screen — logging a mood never requires the full app. It saves
 * through the same /moods API as Home and the chat opener (one write path, the
 * same six wire moods), and when the save can't succeed (guest, offline-hard)
 * it opens the app instead of pretending.
 *
 * Deliberately behind the lock screen's unlock, never over it: a mental-health
 * check-in floating on a locked phone is a privacy leak in family contexts
 * (design rule §9), so no setShowWhenLocked here.
 */
class QuickLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        setContent {
            CereBroTheme {
                var savedLabel by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val openApp = {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    finish()
                }
                savedLabel?.let {
                    // A breath of confirmation, then out of the way.
                    LaunchedEffect(it) { delay(1400); finish() }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardFill)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.quicklog_title),
                        style = MaterialTheme.typography.titleLarge, color = TextPrimary,
                    )
                    if (savedLabel == null) {
                        Text(
                            stringResource(R.string.quicklog_sub),
                            style = MaterialTheme.typography.bodySmall, color = TextMuted,
                        )
                        MOODS.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { mood ->
                                    val label = stringResource(mood.labelRes)
                                    val tint = mood.tint()
                                    // The reference mood-row language: the
                                    // feeling's own hue in a circular well.
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(99.dp))
                                            .border(1.dp, LineStroke, RoundedCornerShape(99.dp))
                                            .clickable(enabled = !busy) {
                                                busy = true
                                                scope.launch {
                                                    val ok = runCatching {
                                                        Api.checkIn(mood.name, mood.note, mood.symbol, mood.intensity)
                                                    }.isSuccess
                                                    if (ok) savedLabel = label else openApp()
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            Modifier.clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(tint.copy(alpha = .14f))
                                                .padding(6.dp),
                                            contentAlignment = androidx.compose.ui.Alignment.Center,
                                        ) {
                                            androidx.compose.material3.Icon(
                                                com.cerebrozen.app.ui.screens.moodIcon(mood.name),
                                                contentDescription = null, tint = tint,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                        Text(label, style = MaterialTheme.typography.titleSmall, color = TextSoft, maxLines = 1)
                                    }
                                }
                                if (pair.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(onClick = { openApp() }) {
                                Text(stringResource(R.string.quicklog_open_app), color = Periwinkle)
                            }
                            TextButton(onClick = { finish() }) {
                                Text(stringResource(R.string.quicklog_not_now), color = TextMuted)
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.quicklog_saved, savedLabel.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium, color = TextSoft,
                        )
                    }
                }
            }
        }
    }
}
