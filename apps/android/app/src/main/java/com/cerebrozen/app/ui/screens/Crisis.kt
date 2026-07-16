package com.cerebrozen.app.ui.screens

/* Crisis support surfaces — recovered intact from the reference during the
 * B2C strip (safety code is a keep-feature; docs/SECURITY.md). */

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cerebrozen.app.BuildConfig
import com.cerebrozen.app.R
import com.cerebrozen.app.data.Helplines
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.ArtScrim
import com.cerebrozen.app.ui.theme.ArtTextSoft
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cream
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.TextBright
import com.cerebrozen.app.ui.theme.VeilWell
import com.cerebrozen.app.ui.theme.EyebrowMuted
import com.cerebrozen.app.ui.theme.Danger
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
import com.cerebrozen.app.ui.theme.Radius
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.delay
import kotlin.random.Random
import org.json.JSONArray


/** True when a support target is a link rather than a dialable number — any
 * letter means URL (phone numbers are digits, dashes and spaces). Shared by the
 * crisis and human-support directories. */
internal fun isSupportUrl(target: String): Boolean = target.any { it.isLetter() }

/** Open a support target: phone numbers open the dialer (never auto-call), URLs
 * open the browser/WhatsApp. Failures are swallowed — a missing handler must
 * never crash a support surface. */
internal fun openSupportTarget(ctx: android.content.Context, target: String) {
    val intent = if (isSupportUrl(target)) {
        Intent(Intent.ACTION_VIEW, Uri.parse(if (target.startsWith("http")) target else "https://$target"))
    } else {
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$target"))
    }
    runCatching { ctx.startActivity(intent) }
}

/** A tappable support line — title, a cyan detail line, and a call/open glyph.
 * The whole card is one accessible target (used by Crisis + Human support). */
@Composable
internal fun SupportLinkRow(title: String, detail: String, target: String) {
    val ctx = LocalContext.current
    val isUrl = isSupportUrl(target)
    val desc = if (isUrl) stringResource(R.string.crisis_open_cd, title)
    else stringResource(R.string.crisis_call_cd, title, detail)
    SectionCard(onClick = { openSupportTarget(ctx, target) }) {
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = desc },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = Cyan)
            }
            Icon(
                if (isUrl) Icons.AutoMirrored.Outlined.OpenInNew else Icons.Outlined.Call,
                contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun CrisisScreen(onBack: () -> Unit) {
    var contact by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Api.trustedContact() }.onSuccess { tc ->
            contact = tc?.let { "${it.optString("name")} · ${it.optString("value")}" }
        }
    }
    // The lines come from the ENGINE, for THIS person's region — they used to be India's
    // numbers as literals here, shown to everyone, while Settings offered a region picker
    // whose answer nothing read (ARCHITECTURE.md §Cross-stack contracts: "never hardcoded
    // in clients"). The W25 note about the dead Tele-MANAS WhatsApp target still holds and
    // now lives with the rest of the directory, in app/safety/helplines.py.
    //
    // Starting from NEUTRAL (not an empty list) is the point: this screen renders
    // something dialable on its very first frame, before any network call resolves, and
    // keeps doing so if every call fails. See data/Helplines.kt.
    var lines by remember { mutableStateOf(Helplines.NEUTRAL) }
    LaunchedEffect(Unit) {
        val region = runCatching { Api.me().optString("crisis_region") }.getOrDefault("")
        lines = Helplines.load(region)
    }
    SubPage(stringResource(R.string.crisis_eyebrow), stringResource(R.string.crisis_title), onBack) {
        GradientHero(
            eyebrow = stringResource(R.string.crisis_hero_eyebrow),
            title = stringResource(R.string.crisis_hero_title),
            colors = listOf(Warm, Danger),
        )
        lines.forEach { line ->
            SupportLinkRow(line.name, line.detail, line.target)
        }
        SectionCard {
            Text(stringResource(R.string.crisis_trusted_contact_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(contact ?: stringResource(R.string.crisis_trusted_contact_empty),
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Text(stringResource(R.string.common_wellness_footer),
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}
