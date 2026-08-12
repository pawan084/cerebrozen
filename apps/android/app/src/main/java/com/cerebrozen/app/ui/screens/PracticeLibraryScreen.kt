package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.Chime
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.ButtonDisabled
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.DangerSoft
import com.cerebrozen.app.ui.theme.FieldFill
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.OkSoft
import com.cerebrozen.app.ui.theme.OnPrimary
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import com.cerebrozen.app.ui.theme.WarmSoft
import kotlinx.coroutines.launch


/** The first level under Explore's Calm now card: broad practice families,
 * before the user commits to a particular exercise. */
@Composable
fun PracticeLibraryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    Column(Modifier.fillMaxSize().background(Night)) {
        CereBroTopBar(
            title = stringResource(R.string.practicelib_title),
            // Was "Five clear families" over six rows.
            subtitle = stringResource(R.string.practicelib_subtitle),
            onBack = onBack,
            onUrgent = { onOpen("crisis") },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp).padding(top = 15.dp, bottom = 20.dp),
        ) {
            Text(
                stringResource(R.string.practicelib_eyebrow),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp),
                color = Warm,
            )
            Text(
                stringResource(R.string.practicelib_hero),
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 43.sp, lineHeight = 39.sp,
                ),
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.practicelib_intro),
                modifier = Modifier.padding(top = 14.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
                color = TextMuted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                PracticeFamilyRow(Icons.Outlined.Spa, stringResource(R.string.practicelib_breathe_title), stringResource(R.string.practicelib_breathe_sub), OkSoft, Ok) { onOpen("breathing-intro") }
                PracticeFamilyRow(Icons.Outlined.HealthAndSafety, stringResource(R.string.practicelib_ground_title), stringResource(R.string.practicelib_ground_sub), WarmSoft, Warm) { onOpen("groundingintro") }
                PracticeFamilyRow(Icons.Outlined.FavoriteBorder, stringResource(R.string.practicelib_reset_title), stringResource(R.string.practicelib_reset_sub), WarmSoft, Warm) { onOpen("tipp") }
                PracticeFamilyRow(Icons.Outlined.Psychology, stringResource(R.string.practicelib_thoughts_title), stringResource(R.string.practicelib_thoughts_sub), WarmSoft, Warm) { onOpen("cbt") }
                PracticeFamilyRow(Icons.Outlined.Bedtime, stringResource(R.string.practicelib_sleep_title), stringResource(R.string.practicelib_sleep_sub), OkSoft, Ok) { onOpen("bodyscan") }
                PracticeFamilyRow(Icons.Outlined.AutoAwesome, stringResource(R.string.practicelib_gratitude_title), stringResource(R.string.practicelib_gratitude_sub), WarmSoft, Warm) { onOpen("gratitude") }
            }
        }
    }
}

@Composable
fun PracticeBreathingScreen(onBack: () -> Unit, onUrgent: () -> Unit, onBegin: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    // Read from — and written back to — the settings the breathing session
    // actually consults. These were three `remember` booleans that the session
    // never saw, so the screen let you turn the chime off and then rang it.
    // "Keep screen awake" is gone rather than fixed: it had no setting behind it
    // either, and the loop screen holds the screen on unconditionally, so an
    // off position was never going to be honoured.
    var chime by remember { mutableStateOf(Chime.breatheChimeEnabled) }
    var haptics by remember { mutableStateOf(Chime.breatheHapticsEnabled) }
    Column(Modifier.fillMaxSize().background(Night)) {
        CereBroTopBar(
            title = stringResource(R.string.breathing_title),
            subtitle = stringResource(R.string.breathprep_subtitle),
            onBack = onBack,
            onUrgent = onUrgent,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 15.dp),
        ) {
            Text(
                stringResource(R.string.breathprep_eyebrow),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp),
                color = Warm,
            )
            Text(
                stringResource(R.string.breathprep_hero), modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp),
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.breathprep_intro),
                modifier = Modifier.padding(top = 13.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = TextMuted,
            )
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(FieldFill).padding(horizontal = 18.dp),
            ) {
                BreathingSetting(
                    stringResource(R.string.breathprep_chime_title),
                    stringResource(R.string.breathprep_chime_sub),
                    chime,
                ) { chime = it; Chime.breatheChimeEnabled = it }
                BreathingSetting(
                    stringResource(R.string.breathprep_haptics_title),
                    stringResource(R.string.breathprep_haptics_sub),
                    haptics, showDivider = false,
                ) { haptics = it; Chime.breatheHapticsEnabled = it }
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 11.dp).height(49.dp).clip(RoundedCornerShape(25.dp))
                    .background(Periwinkle).clickable(onClick = onBegin),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.breathprep_begin), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OnPrimary)
            }
        }
    }
}


@Composable
fun GratitudeReflectionScreen(onBack: () -> Unit, onUrgent: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    var reflection by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Night)) {
        PracticeHeader(
            stringResource(R.string.practice_gratitude_title),
            stringResource(R.string.practice_gratitude_subtitle),
            onBack, onUrgent,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 15.dp),
        ) {
            Text(
                stringResource(R.string.practice_gratitude_eyebrow),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp),
                color = Warm,
            )
            Text(
                stringResource(R.string.practice_gratitude_hero),
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp,
                ),
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.practice_gratitude_intro),
                modifier = Modifier.padding(top = 13.dp, bottom = 17.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                color = TextMuted,
            )
            OutlinedTextField(
                value = reflection,
                onValueChange = { reflection = it; status = null },
                placeholder = { Text(stringResource(R.string.practice_gratitude_placeholder), color = TextMuted) },
                modifier = Modifier.fillMaxWidth().height(78.dp),
                shape = RoundedCornerShape(18.dp),
                minLines = 2,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardFill, unfocusedContainerColor = CardFill,
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                ),
            )
            // The reflection is a real journal entry, not a local flag: this
            // button used to set `saved = true` and relabel itself "Saved
            // privately" while the text was never written anywhere, so the
            // promise was false and the writing was lost on navigation.
            val entryTitle = stringResource(R.string.practice_gratitude_entry_title)
            val savedStatus = stringResource(R.string.journal_saved)
            val queuedStatus = stringResource(R.string.practice_gratitude_queued)
            val saveFailed = stringResource(R.string.common_save_failed)
            val enabled = !busy && reflection.isNotBlank()
            Box(
                Modifier.fillMaxWidth().padding(top = 17.dp).height(49.dp).clip(RoundedCornerShape(25.dp))
                    .background(if (enabled) Periwinkle else ButtonDisabled)
                    .clickable(enabled = enabled) {
                        busy = true; status = null
                        scope.launch {
                            try {
                                // Null = queued by the outbox with no connection.
                                // Saying "saved" flatly would re-tell the old lie
                                // in a quieter way, so the two outcomes read
                                // differently.
                                val entry = Api.createJournal(entryTitle, reflection.trim())
                                status = if (entry != null) savedStatus else queuedStatus
                                reflection = ""
                            } catch (e: Exception) {
                                status = e.message ?: saveFailed
                            } finally {
                                busy = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (busy) stringResource(R.string.common_one_moment)
                    else stringResource(R.string.practice_gratitude_save),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
            status?.let {
                Text(
                    it, modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
            }
            Text(
                stringResource(R.string.practice_gratitude_skip),
                modifier = Modifier.padding(start = 7.dp, top = 20.dp).clickable(onClick = onBack),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Periwinkle,
            )
        }
    }
}

@Composable
fun UrgentSupportScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    val region by rememberCrisisRegion()
    val regional = crisisLinesFor(region)
    val emergency = regional.firstOrNull { it.target in setOf("112", "911", "999", "000", "111") } ?: regional.first()
    val mental = primaryCrisisLine(region)
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Night)) {
        CereBroTopBar(
            title = stringResource(R.string.crisis_title),
            subtitle = stringResource(R.string.urgent_subtitle),
            onBack = onBack,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DangerSoft).padding(18.dp)) {
                Text(
                    stringResource(R.string.urgent_danger_title, emergency.target),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary,
                )
                Text(
                    stringResource(R.string.urgent_danger_body),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp), color = TextSoft,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Region only. This used to read "INDIA · ENGLISH" with the
                // language hardcoded, which was wrong for anyone who had picked
                // Hindi and told them nothing about the numbers either way.
                Text(
                    stringResource(regionLabelRes(region)).uppercase(),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = .8.sp), color = Warm,
                )
                Box(Modifier.clip(CircleShape).background(OkSoft).padding(horizontal = 13.dp, vertical = 7.dp)) {
                    Text(stringResource(R.string.urgent_verified), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Ok)
                }
            }
            Text(
                stringResource(R.string.urgent_hero),
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp),
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.urgent_intro),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = TextMuted,
            )
            UrgentAction(
                stringResource(R.string.urgent_emergency_title),
                stringResource(R.string.urgent_emergency_detail, emergency.target),
                Icons.Outlined.Call, primary = true,
            ) { openSupportTarget(context, emergency.target) }
            UrgentAction(
                stringResource(R.string.urgent_call_line, stringResource(mental.nameRes)),
                stringResource(
                    if (region == "IN") R.string.urgent_line_detail_in else R.string.urgent_line_detail_other,
                    mental.target,
                ),
                Icons.Outlined.FavoriteBorder,
            ) { openSupportTarget(context, mental.target) }
            UrgentAction(
                stringResource(R.string.urgent_trusted_title),
                stringResource(R.string.urgent_trusted_detail),
                Icons.Outlined.FavoriteBorder,
            ) { onOpen("trustedcontact") }
            UrgentAction(
                stringResource(R.string.urgent_cannot_call_title),
                stringResource(R.string.urgent_cannot_call_detail),
                Icons.Outlined.Spa, sage = true,
            ) { onOpen("crisisgrounding") }
            androidx.compose.material3.HorizontalDivider(color = LineStroke.copy(alpha = .7f))
            Text(stringResource(R.string.urgent_cached_title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextMuted)
            Text(
                stringResource(R.string.urgent_cached_body),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = TextMuted,
            )
            Text(
                stringResource(R.string.crisis_region_change),
                modifier = Modifier.padding(7.dp).clickable { onOpen("crisisregion") },
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Periwinkle,
            )
            Box(
                Modifier.fillMaxWidth().height(49.dp).clip(RoundedCornerShape(25.dp)).background(CardFill)
                    .border(1.dp, LineStroke, RoundedCornerShape(25.dp)).clickable { onOpen("safetyplan") },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.urgent_open_safety_plan), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Periwinkle) }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun UrgentAction(title: String, detail: String, icon: ImageVector, primary: Boolean = false, sage: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (primary) 86.dp else 94.dp)
            .shadow(7.dp, shape, ambientColor = Color.Black.copy(alpha = .06f)).clip(shape)
            .background(if (primary) Danger else CardFill)
            .then(if (primary) Modifier else Modifier.border(.5.dp, LineStroke.copy(alpha = .7f), shape))
            .clickable(onClick = onClick).padding(horizontal = 17.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(43.dp).clip(CircleShape).background(if (primary) DangerSoft else if (sage) OkSoft else DangerSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (primary) Danger else if (sage) Ok else Danger, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (primary) Color.White else TextPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = if (primary) Color.White.copy(alpha = .92f) else TextMuted)
        }
    }
}

@Composable
private fun PracticeHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onUrgent: () -> Unit,
) = CereBroTopBar(title = title, subtitle = subtitle, onBack = onBack, onUrgent = onUrgent)

@Composable
private fun BreathingSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(73.dp).then(if (showDivider) Modifier.border(0.dp, Color.Transparent) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Periwinkle),
        )
    }
}

@Composable
private fun PracticeFamilyRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconFill: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(77.dp)
            .shadow(9.dp, RoundedCornerShape(23.dp), ambientColor = Color.Black.copy(alpha = .08f))
            .clip(RoundedCornerShape(23.dp)).background(CardFill)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(43.dp).clip(CircleShape).background(iconFill), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = Periwinkle)
    }
}
