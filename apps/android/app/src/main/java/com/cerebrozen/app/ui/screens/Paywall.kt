package com.cerebrozen.app.ui.screens

/* CereBro Plus — the consumer upgrade screen (B2C freemium).
 *
 * Reached two ways: tapped from the "CereBro Plus" row in You, or shown in place
 * when a free account opens a Plus-only route (sleep, insights, patterns, sounds).
 * In the second case the parent route is `if (Session.entitled(x)) RealScreen else
 * PaywallScreen`, so the moment a purchase flips the observable entitlements this
 * whole screen is replaced by the real one — no manual navigation needed.
 *
 * Purchase runs through Session.startPlus(), which is provider-abstracted: the mock
 * provider (default, keyless) completes in-process; a real Stripe/Play flow returns
 * already active. No card data ever touches the app or our servers. */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Session
import kotlinx.coroutines.launch

/** One Plus benefit, keyed by the entitlement it maps to so we can spotlight the
 *  one the person just hit a wall on. */
private val PLUS_BENEFITS = listOf(
    "voice" to "Talk out loud — voice in, spoken replies",
    "coach_daily_limit" to "Unlimited coaching — no daily cap",
    "all_programs" to "Every guided program, not just one",
    "sleep" to "Sleep tracking and trends",
    "insights" to "Weekly insights",
    "patterns" to "The pattern dashboard — what your coach has learned",
    "soundscapes" to "Ambient soundscapes and the mixer",
    "journal_memory" to "Journal memory — your coach recalls your entries",
)

// Mirrors the platform's billing.PRICES (USD, minor units). Display only.
private const val YEARLY = "$59.99 / year"
private const val MONTHLY = "$9.99 / month"

@Composable
fun PaywallScreen(onBack: () -> Unit, highlight: String = "") {
    val scope = rememberCoroutineScope()
    var interval by remember { mutableStateOf("yearly") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var purchased by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }

    val accent = Color(0xFFB18CFF)

    PremiumSubPage(
        eyebrow = if (Session.isPlus) "YOUR PLAN" else "CEREBRO PLUS",
        title = when {
            purchased -> "You're on Plus"
            Session.isPlus -> "You're on CereBro Plus"
            else -> "Unlock everything"
        },
        onBack = onBack,
    ) {
        if (purchased || Session.isPlus) {
            PremiumStateCard(
                icon = Icons.Outlined.WorkspacePremium,
                message = "CereBro Plus is active — every feature is unlocked. " +
                    "Manage or cancel anytime; you keep access until the period ends.",
                accent = accent,
                actionLabel = "Continue",
                onAction = onBack,
            )
            Spacer(Modifier.height(14.dp))
            if (Session.isPlus && !purchased) {
                DangerButton(
                    text = if (busy) "Cancelling…" else "Cancel Plus",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) return@DangerButton
                    busy = true; error = null
                    scope.launch {
                        runCatching { Session.cancelPlus() }
                            .onFailure { error = "Couldn't cancel just now — try again." }
                        busy = false
                    }
                }
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFFF9E9E), style = MaterialTheme.typography.bodySmall)
            }
            return@PremiumSubPage
        }

        // A one-line reason, spotlighting the wall they hit if we know it.
        val reason = PLUS_BENEFITS.firstOrNull { it.first == highlight }?.second
        Text(
            reason?.let { "$it — and more, with Plus." }
                ?: "Go deeper with your coach. Core check-ins, the coach, and crisis support stay free, always.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFD2D9EB),
        )
        Spacer(Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PLUS_BENEFITS.forEach { (key, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(26.dp).background(accent.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (key == highlight) Color.White else Color(0xFFC6CFE4),
                        fontWeight = if (key == highlight) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))

        // Billing interval.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IntervalChip("Yearly", YEARLY, "Best value", interval == "yearly", accent, Modifier.weight(1f)) { interval = "yearly" }
            IntervalChip("Monthly", MONTHLY, null, interval == "monthly", accent, Modifier.weight(1f)) { interval = "monthly" }
        }
        Spacer(Modifier.height(18.dp))

        PrimaryButton(
            text = if (busy) "Starting…" else "Start CereBro Plus",
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            busy = true; error = null
            scope.launch {
                runCatching { Session.startPlus(interval) }
                    .onSuccess { purchased = true }
                    .onFailure { error = "Couldn't start Plus just now — please try again." }
                busy = false
            }
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Color(0xFFFF9E9E), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Auto-renews until cancelled. Cancel anytime in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8C97AE),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // Restore purchases (store requirement + honest recovery path): re-reads the plan
        // from /billing/me. If this device already owns Plus — a reinstall, a new phone, or a
        // purchase that completed elsewhere — the observable entitlements flip and the gated
        // route that showed this paywall is replaced by the real screen automatically.
        Spacer(Modifier.height(2.dp))
        Text(
            if (restoring) "Restoring…" else "Restore purchases",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFC6CFE4),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !restoring && !busy) {
                    restoring = true; error = null
                    scope.launch {
                        runCatching { Session.refreshBilling() }
                        restoring = false
                        if (Session.isPlus) purchased = true
                        else error = "No active subscription found to restore."
                    }
                }
                .padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun IntervalChip(
    label: String,
    price: String,
    tag: String?,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .background(
                if (selected) accent.copy(alpha = 0.16f) else Color(0x1AFFFFFF),
                shape,
            )
            .border(1.dp, if (selected) accent.copy(alpha = 0.7f) else Color(0x22FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
        Text(price, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC6CFE4))
        if (tag != null) Text(tag, style = MaterialTheme.typography.labelSmall, color = accent)
    }
}
