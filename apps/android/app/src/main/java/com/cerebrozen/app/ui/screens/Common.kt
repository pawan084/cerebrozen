package com.cerebrozen.app.ui.screens

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.AppTheme
import com.cerebrozen.app.ui.theme.BrandPrimary
import com.cerebrozen.app.ui.theme.Elevation
import com.cerebrozen.app.ui.theme.Gradients
import com.cerebrozen.app.ui.theme.Radius
import com.cerebrozen.app.ui.theme.Space
import com.cerebrozen.app.ui.theme.Stroke
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.ButtonDisabled
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.CardShadow
import com.cerebrozen.app.ui.theme.ChipFill
import com.cerebrozen.app.ui.theme.ChipSelectedFill
import com.cerebrozen.app.ui.theme.ChipSelectedInk
import com.cerebrozen.app.ui.theme.OnPrimary
import com.cerebrozen.app.ui.theme.PrimaryButtonFill
import com.cerebrozen.app.ui.theme.PrimaryButtonInk
import com.cerebrozen.app.ui.theme.PrimaryButtonDisabledFill
import com.cerebrozen.app.ui.theme.SwitchThumbOn
import com.cerebrozen.app.ui.theme.ShimmerHighlight
import com.cerebrozen.app.ui.theme.Veil
import com.cerebrozen.app.ui.theme.VeilSoft
import com.cerebrozen.app.ui.theme.EyebrowMuted
import com.cerebrozen.app.ui.theme.FieldFill
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.NightPurple
import com.cerebrozen.app.ui.theme.OnDanger
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.AccentSoft
import com.cerebrozen.app.ui.theme.SurfaceRaised
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSecondary
import com.cerebrozen.app.ui.theme.TextSoft

private val CardShape = RoundedCornerShape(Radius.card)

// Responsive sizing helpers — pages and cards breathe a little tighter on small
// phones and a touch more generously on large ones, instead of one fixed inset.
@Composable
internal fun isCompactWidth(): Boolean = LocalConfiguration.current.screenWidthDp < 380

@Composable
internal fun pageHorizontalPadding() = when {
    LocalConfiguration.current.screenWidthDp < 360 -> 18.dp
    LocalConfiguration.current.screenWidthDp < 420 -> 22.dp
    else -> 24.dp
}

@Composable
internal fun cardPadding() = when {
    LocalConfiguration.current.screenWidthDp < 360 -> 16.dp
    LocalConfiguration.current.screenWidthDp < 420 -> 18.dp
    else -> 20.dp
}

/** The one section break. [Page] spaces its children by [Space.item] (12dp), so a
 * SectionGap adds [Space.group] on top to reach the 28dp section rhythm. Three
 * tiers of spacing, so proximity actually groups things instead of every element
 * floating at an identical distance from every other. */
@Composable
internal fun SectionGap() = Spacer(Modifier.height(Space.group))

/** The shared card surface treatment: a top-lit solid indigo fill, a soft lift, and
 * a *bevelled* hairline — the border is a vertical gradient (bright at the top edge,
 * fading down) so the pane catches light like a real bevelled edge rather than
 * reading as a flat outline. An honest soft-solid: the fill is opaque, so there is
 * no backdrop blur behind it (REDESIGN.md §4.1). */
/**
 * Let a child run past the page's horizontal padding, to the screen edges.
 *
 * For horizontal rails. Inside the 24dp inset a rail's last card stops short of
 * the edge, so a scrolling row reads as a finished two-column grid — the one
 * universal signal that there is more sideways is a card cut off by the screen.
 * It also breaks the uniform inset every other element shares, which is most of
 * why the screen felt like one stack of identical rectangles.
 *
 * Measures the child wider by [horizontal] on each side and places it back at
 * the negative offset, so the parent's layout is unchanged.
 */
internal fun Modifier.bleed(horizontal: Dp): Modifier = this.layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
            maxWidth = if (constraints.maxWidth == Constraints.Infinity) {
                constraints.maxWidth
            } else {
                constraints.maxWidth + extra
            },
        ),
    )
    layout(placeable.width - extra, placeable.height) {
        placeable.place(-horizontal.roundToPx(), 0)
    }
}

/**
 * A secondary surface: the fill and hairline, without the lift.
 *
 * Hierarchy, not decoration. Every card on Home used [glass], so the check-in,
 * the plan hero, a nav row and a list of past check-ins all claimed the same
 * importance — which is another way of saying none of them did. Quiet cards
 * carry the things you read after you have done the thing the screen asked for.
 */
internal fun Modifier.quiet(shape: Shape = CardShape): Modifier = this
    .clip(shape)
    .background(Veil)
    .border(1.dp, Stroke.hairline, shape)

internal fun Modifier.glass(shape: Shape = CardShape): Modifier = this
    // CardShadow, not fixed literals: on Dawn the shadow is what carries depth
    // (a near-white card on a warm ground can't separate by value alone), so the
    // elevation and both shadow colours are theme-resolved.
    .shadow(
        CardShadow.elevation, shape, clip = false,
        ambientColor = CardShadow.ambient, spotColor = CardShadow.spot,
    )
    .clip(shape)
    .background(Gradients.glass)
    // A faint brand-light edge keeps large card stacks from reading as flat
    // grey rectangles, while remaining subtle enough for dense settings pages.
    .background(
        Brush.linearGradient(
            listOf(Periwinkle.copy(alpha = 0.055f), Color.Transparent, Cyan.copy(alpha = 0.035f)),
        ),
    )
    .border(1.dp, Stroke.bevel, shape)

/** True when the user has asked the system to minimise animations ("Remove
 * animations" / animator duration scale = 0) — the Android analogue of iOS's
 * Reduce Motion. Entrances and looping animations honour this; discrete press /
 * selection feedback stays (matching the iOS policy). */
/** Pure predicate seam for [rememberReduceMotion]: animations are "reduced" when
 * the system animator duration scale is 0 ("Remove animations"). Kept separate so
 * the branch is unit-testable without a composition. */
internal fun reduceMotionFromScale(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/** An ambient float that truly IDLES under Reduce Motion.
 *
 * The recurring bug (audit B23-B27): sites gated the READ
 * (`if (reduceMotion) still else v`) while the infinite transition kept its
 * clock running, recomposing the surface forever for users who asked for
 * stillness. Branching here means no transition is even created when motion
 * is reduced. */
@Composable
internal fun restingFloat(
    reduceMotion: Boolean,
    still: Float,
    initial: Float,
    target: Float,
    spec: androidx.compose.animation.core.InfiniteRepeatableSpec<Float>,
    label: String,
): Float {
    if (reduceMotion) return still
    val transition = rememberInfiniteTransition(label = label)
    val value by transition.animateFloat(initial, target, spec, label = label)
    return value
}

@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    // Observed, not sampled once: toggling "Remove animations" in Settings used
    // to have no effect until the Activity was recreated. Settings.Global is a
    // ContentProvider, so we register for the key and re-read on every change.
    val resolver = context.contentResolver
    var reduced by remember(context) {
        mutableStateOf(
            reduceMotionFromScale(
                Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
            ),
        )
    }
    DisposableEffect(resolver) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = reduceMotionFromScale(
                    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
                )
            }
        }
        runCatching { resolver.registerContentObserver(uri, false, observer) }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return reduced
}

/** A soft press-in: the target scales down slightly while held and springs back on
 * release. Keeps taps tactile without being bouncy — calm, not playful. Feed it a
 * [pressed] flag from an interaction source. */
@Composable
internal fun Modifier.pressScale(pressed: Boolean, down: Float = 0.96f): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (pressed) down else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A gentle one-shot entry: content rises a few dp and fades in on first
 * composition. Pass an [index] to stagger siblings into a soft cascade;
 * [durationMs] tightens the entrance for small, frequent arrivals (chat bubbles). */
@Composable
internal fun Modifier.appear(index: Int = 0, rise: Float = 20f, durationMs: Int = 440): Modifier {
    val reduceMotion = rememberReduceMotion()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) { anim.snapTo(1f); return@LaunchedEffect }  // no entrance — settle instantly
        kotlinx.coroutines.delay(index * 55L)
        anim.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        translationY = (1f - anim.value) * rise
        alpha = anim.value
    }
}

/** A gentle one-shot fill: content fades and scales in (0.6 → 1.0) on first
 * composition, staggered [stepMs] apart by [index] — the presence-ring dots'
 * entrance. Under Reduce Motion it settles instantly (static, never blank). */
@Composable
internal fun Modifier.popIn(index: Int = 0, stepMs: Long = 40L): Modifier {
    val reduceMotion = rememberReduceMotion()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) { anim.snapTo(1f); return@LaunchedEffect }  // no entrance — settle instantly
        kotlinx.coroutines.delay(index * stepMs)
        anim.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val s = 0.6f + 0.4f * anim.value
        scaleX = s
        scaleY = s
        alpha = anim.value
    }
}

/** A one-shot soft ring blooming outward over a card after a successful save —
 * a small, calm reward, deliberately NOT the full-screen celebration. Re-arms
 * each time [trigger] increments; callers skip arming it under Reduce Motion
 * (their confirmation line is the state change there). Extracted from the Home
 * check-in (E2) so Journal and future surfaces share one bloom. */
@Composable
internal fun BloomRing(trigger: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    val p = progress.value
    if (p >= 1f) return   // finished — draw nothing until the next trigger
    Canvas(modifier) {
        val ringScale = 0.6f + 0.8f * p            // 0.6 → 1.4
        drawCircle(
            color = color.copy(alpha = 0.4f * (1f - p)),   // 0.4 → 0
            radius = size.minDimension / 2f * ringScale,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
        )
    }
}

/**
 * The streak-milestone halo (iOS parity: `RadiatingRing` in Components.swift) —
 * a single hairline ring that swells outward and fades, forever, slowly. Marks a
 * milestone the presence card *already* announces in words; it adds no claim of
 * its own, which is why it is safe to run unattended.
 *
 * Same numbers as iOS so the two feel like one product: 0.6 → 1.35 scale, 0.5 → 0
 * opacity, 2s, ease-out, no reversal.
 *
 * [enabled] is a parameter rather than a bare Reduce-Motion read for the reason
 * iOS gates on `-resetState`: this is an *endless* animation, and a composition
 * that never goes idle is a test that never finishes. Nothing renders Home in the
 * unit suite today; the seam is here so that stays a choice rather than a trap.
 */
@Composable
internal fun RadiatingRing(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    color: Color = Periwinkle,
    enabled: Boolean = !rememberReduceMotion(),
) {
    if (!enabled) return
    val transition = rememberInfiniteTransition(label = "radiating-ring")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radiate",
    )
    Canvas(modifier.size(size)) {
        drawCircle(
            color = color.copy(alpha = 0.5f * (1f - p)),
            radius = this.size.minDimension / 2f * (0.6f + 0.75f * p),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )
    }
}

/**
 * An occasional diagonal light sweep (iOS parity: `sheen()`), for the one upsell
 * surface — distinct from [ShimmerBox], which means "still loading". A sheen on
 * something that is not loading would otherwise read as a stuck spinner.
 *
 * Deliberately sparse: the sweep itself takes ~1.1s of a [periodMillis] cycle, so
 * the row rests still most of the time. Reduce Motion removes it entirely rather
 * than slowing it — there is no information in it to preserve.
 *
 * The highlight is theme-aware, which iOS does not need to be. A white sweep is
 * what reads on Night, and it is invisible on Dawn's near-white card — checked on
 * a device, where the row was indistinguishable from its neighbours. Dawn gets the
 * lavender accent instead, so the row actually catches light in both themes.
 */
@Composable
internal fun Modifier.sheen(
    periodMillis: Int = 5500,
    shape: Shape = RoundedCornerShape(20.dp),
    enabled: Boolean = !rememberReduceMotion(),
): Modifier {
    if (!enabled) return this
    val transition = rememberInfiniteTransition(label = "sheen")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sheen-phase",
    )
    // The sweep occupies the first fifth of the cycle; the rest is stillness.
    val sweep = (phase / 0.2f).coerceAtMost(1f)
    val visible = phase < 0.2f
    val highlight = if (AppTheme.isNight) Color.White.copy(alpha = 0.13f)
                    else Periwinkle.copy(alpha = 0.20f)
    return this.drawWithContent {
        drawContent()
        if (!visible) return@drawWithContent
        val band = size.width * 0.35f
        val x = -band + (size.width + band * 2f) * sweep
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                startX = x - band / 2f,
                endX = x + band / 2f,
            ),
            size = size,
        )
    }.clip(shape)
}

/** W24 D2: a small kind-matched art medallion for empty states — the same
 * generative ContentArt system the tiles use, sized as a quiet illustration
 * (48–64dp) above the empty copy. Appears once with a gentle settle
 * (fade + 0.92 → 1, ~320ms); Reduce Motion rests it at the final frame.
 * Decorative only — no contentDescription, no copy of its own. */
@Composable
internal fun EmptyStateArt(kind: String, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val settle = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { settle.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val s = 0.92f + 0.08f * settle.value
                scaleX = s
                scaleY = s
                alpha = settle.value
            }
            .clip(RoundedCornerShape(16.dp)),
    ) {
        // The kind doubles as the seed title (like the InfoBanner medallion),
        // so each surface's illustration is stable across visits.
        ContentArt(title = kind, kind = kind, modifier = Modifier.fillMaxSize())
    }
}

/** A quiet, single-purpose banner (W9 B5): SurfaceRaised fill, a soft accent
 * hairline, an accent-tinted icon and calm supporting copy — with an optional
 * small accent action and an optional dismiss. Deliberately unanimated beyond
 * the shared [appear] entrance; never taller than ~72dp.
 *
 * W21: pass [artKind] on CONTENT banners (program day strip, evening
 * wind-down) to lead with a 40dp generative-art medallion instead of the bare
 * icon, plus a very subtle leading wash of the kind's accent (10% fading to
 * transparent by mid-banner, so the copy and the trailing action sit on plain
 * SurfaceRaised — ContrastTest gates the worst-case blend in both themes).
 * Utility banners (offline, morning check-in) stay icon-only. */
@Composable
internal fun InfoBanner(
    icon: ImageVector,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    artKind: String? = null,
) {
    val shape = RoundedCornerShape(Radius.card)
    val wash = artKind?.let {
        Brush.horizontalGradient(
            0f to artAccent(it).copy(alpha = 0.10f),
            0.55f to Color.Transparent,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .appear()
            .clip(shape)
            .background(SurfaceRaised)
            .let { m -> if (wash != null) m.background(wash) else m }
            .border(1.dp, AccentSoft.copy(alpha = 0.35f), shape)
            .padding(
                start = if (artKind != null) 8.dp else 14.dp,
                end = if (onDismiss != null) 2.dp else 8.dp,
                top = 6.dp, bottom = 6.dp,
            )
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artKind != null) {
            // The kind doubles as the art's seed title so every banner of one
            // kind wears the same, stable medallion. W24: it settles in once
            // (0.92 → 1, 250ms) on first composition; Reduce Motion rests it
            // at full size from the first frame.
            val reduceMotion = rememberReduceMotion()
            val settle = remember { Animatable(if (reduceMotion) 1f else 0f) }
            LaunchedEffect(Unit) { settle.animateTo(1f, tween(250, easing = FastOutSlowInEasing)) }
            Box(
                Modifier.size(40.dp)
                    .graphicsLayer {
                        val s = 0.92f + 0.08f * settle.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                ContentArt(title = artKind, kind = artKind, modifier = Modifier.fillMaxSize())
            }
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = Accent.default,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)   // a11y floor, still inside the ≤72dp banner
                    .clip(RoundedCornerShape(Radius.round))
                    .clickable { onAction() }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent.default,
                )
            }
        }
        if (onDismiss != null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.common_dismiss),
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * A failure message that is safe to put in front of a user.
 *
 * `it.userMessage(fallback)` was the idiom at 19 call sites, and it leaks whatever
 * the JVM threw: pulling the plug on the dev stack put "Failed to connect to
 * localhost/127.0.0.1:8000" on the Programs screen, and in the field it would be
 * "Unable to resolve host ..." or a JSON parse error. None of that means anything
 * to someone who just lost signal.
 *
 * [Session.ApiException] is the exception: its message is the server's own
 * `detail`, already curated for humans in `Session.raw` (including the free-tier
 * cap and the rate limiter). Everything else falls back to the caller's own
 * localized line, which is specific to the screen and says something useful.
 */
internal fun Throwable.userMessage(fallback: String): String =
    (this as? Session.ApiException)?.message?.takeIf { it.isNotBlank() } ?: fallback

/**
 * Shared page frame for the live tabs: eyebrow + large rounded title + scroll column.
 *
 * [trailing] renders as a soft icon well top-right. Pass [onTrailingClick] to make
 * it a real control (48dp target, Role.Button, labelled by [trailingLabel]);
 * without it the well stays decorative at 40dp, as before. Home needed a *tappable*
 * search here and so had forked its own header — this closes that fork, which is
 * why the five tabs now share one frame rather than four sharing one and Home
 * hand-rolling a fifth that drifted out of alignment by 4dp.
 *
 * [eyebrowColor] lets a screen tint its eyebrow with the section accent (Home
 * carries the user's goal there — it earns the colour). Defaults to the quiet
 * [EyebrowMuted] every other tab uses.
 */
@Composable
internal fun Page(
    eyebrow: String,
    title: String,
    trailing: ImageVector? = null,
    accent: Color = Accent.default,
    scrollState: ScrollState = rememberScrollState(),
    eyebrowColor: Color = EyebrowMuted,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    /** Pinned below the scrolling body, outside [scrollState].
     *
     * For a composer that must not travel with the transcript. Talk had its
     * message field at the end of the scrolling content, so after any real
     * conversation you had to scroll to the bottom to type — and worse, its
     * auto-scroll-on-new-reply went to `maxValue`, the very bottom of the PAGE,
     * which is the composer and not the reply it meant to reveal. */
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Gentle content-rise on entry (complements the NavHost cross-fade).
    val reduceMotion = rememberReduceMotion()
    val rise = remember { Animatable(26f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) rise.snapTo(0f) else rise.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
    }
    // statusBarsPadding on the CONTAINER, before verticalScroll, so the scrolling
    // viewport itself starts below the status bar. It used to sit behind it, and
    // only the 28dp of content padding — which scrolls away with everything else
    // — kept text clear. Seen on device: with the keyboard up, a Talk bubble was
    // drawn straight through the clock and the signal icons.
    //
    // The top content padding drops to 6dp to compensate, so headers land where
    // they always did rather than every screen shifting down by a status bar.
    // The aurora backdrop is drawn behind the Scaffold, so it stays edge to edge.
    // statusBarsPadding + imePadding on the OUTER container, so the scroll body
    // and any pinned footer both shrink to the space the keyboard leaves. Under
    // edge-to-edge, adjustResize does not literally resize the window — the IME
    // arrives as an inset — so this is the half that keeps a composer reachable,
    // while the manifest's adjustResize is the half that stops the OS panning
    // content up behind the status bar. Neither works without the other.
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(scrollState)
            .graphicsLayer { translationY = rise.value }
            // Responsive inset (18/22/24dp by width) rather than a fixed 24 —
            // and the three-tier Space rhythm SectionGap is built on.
            .padding(start = pageHorizontalPadding(), end = pageHorizontalPadding(), top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(Space.item),
    ) {
        PageHeader(
            eyebrow = eyebrow,
            title = title,
            trailing = trailing,
            accent = accent,
            eyebrowColor = eyebrowColor,
            trailingLabel = trailingLabel,
            onTrailingClick = onTrailingClick,
        )
        content()
    }
    footer?.let { pinned ->
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Night)))
                // imePadding lives on the outer container, not here: applying
                // it at both levels counts the keyboard twice and throws the
                // composer to the top of the screen. Seen on device.
                .padding(horizontal = 24.dp)
                .padding(top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = pinned,
        )
    }
    }
}

/**
 * The tab header on its own: eyebrow + large title + optional icon well.
 *
 * Extracted from [Page] so a screen that cannot live inside one scrolling Column —
 * Talk, which must dock a composer above the keyboard while its thread scrolls
 * independently — gets the identical header instead of hand-rolling a lookalike
 * that drifts out of alignment. [Page] is now a thin wrapper over it.
 */
@Composable
internal fun PageHeader(
    eyebrow: String,
    title: String,
    trailing: ImageVector? = null,
    accent: Color = Accent.default,
    eyebrowColor: Color = EyebrowMuted,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    val headerShape = RoundedCornerShape(Radius.hero)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(
                Elevation.card, headerShape, clip = false,
                ambientColor = accent.copy(alpha = 0.22f),
                spotColor = accent.copy(alpha = 0.28f),
            )
            .clip(headerShape)
            .background(Gradients.glass)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.14f), Color.Transparent, Cyan.copy(alpha = 0.05f)),
                ),
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(accent.copy(alpha = 0.42f), Stroke.hairline, Cyan.copy(alpha = 0.18f))),
                headerShape,
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.tight)) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = eyebrowColor)
            Text(
                title,
                // A soft accent glow behind the title — subtle depth, tinted per
                // section. The size comes from the type scale (displayLarge) rather
                // than a per-call-site override.
                style = MaterialTheme.typography.displayLarge.copy(
                    shadow = Shadow(accent.copy(alpha = 0.28f), Offset.Zero, 24f),
                ),
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.let { icon ->
            val shape = RoundedCornerShape(Radius.round)
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val tappable = onTrailingClick != null
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .then(if (tappable) Modifier.pressScale(pressed) else Modifier)
                    .size(if (tappable) 48.dp else 40.dp)
                    .clip(shape)
                    .background(Veil)
                    .border(1.dp, LineStroke, shape)
                    .then(
                        if (onTrailingClick != null) {
                            Modifier
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    role = Role.Button,
                                ) { Haptics.soft(0.4f); onTrailingClick() }
                                .semantics { trailingLabel?.let { contentDescription = it } }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = TextSoft, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * The primary-action card — one per screen, at most.
 *
 * Every card on Home used to be a [SectionCard]: the daily check-in (the one thing
 * the product wants you to do) looked exactly like the read-only "Recent check-ins"
 * list. Same fill, same radius, same title colour. FocusCard breaks that tie — a
 * larger radius, a real lift, and a 10% brand wash rising from the bottom edge, so
 * the eye lands on the action before it lands on anything else.
 *
 * The wash uses the BRAND hue (a fill, not a label), and every text role on the
 * resulting blend is contrast-gated in both themes by
 * `ContrastTest.focusCard_brandWash_keepsTextAA_inBothThemes`.
 */
@Composable
internal fun FocusCard(
    accent: Color = BrandPrimary,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.hero)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(
                Elevation.focus, shape, clip = false,
                ambientColor = accent.copy(alpha = 0.30f),
                spotColor = accent.copy(alpha = 0.36f),
            )
            .clip(shape)
            .background(Gradients.glass)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to accent.copy(alpha = 0.10f),
                ),
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.14f)),
                ),
                shape,
            )
            .padding(cardPadding()),
        verticalArrangement = Arrangement.spacedBy(Space.item),
    ) { content() }
}

/** Glass card container matching the design system. Pass [onClick] to make the
 * whole card a single tappable, accessible surface. */
@Composable
internal fun SectionCard(
    onClick: (() -> Unit)? = null,
    /** Secondary content: the same card, without the lift. See [Modifier.quiet]. */
    quiet: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val surface: Modifier.() -> Modifier = if (quiet) ({ quiet() }) else ({ glass() })
    val mod = if (onClick != null) {
        // A whisper of press-in — cards are large, so the scale is tiny on purpose.
        modifier.fillMaxWidth()
            .pressScale(pressed, down = 0.985f)
            .surface()
            .clickable(interactionSource = interaction, indication = null) { Haptics.soft(0.4f); onClick() }
    } else {
        modifier.fillMaxWidth().surface()
    }
    Column(
        mod.padding(cardPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** Primary CTA — a near-white pill with dark, bold text. Reads as the one action
 * that matters; the dimmed disabled state still looks intentional. */
@Composable
internal fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Radius.pill)
    Box(
        modifier
            .pressScale(pressed, down = 0.97f)
            // The one action that matters gets a real lift — a soft lavender glow
            // rather than a grey drop shadow, so the CTA feels warm, not heavy.
            .shadow(
                if (enabled) Elevation.card else 0.dp, shape, clip = false,
                ambientColor = BrandPrimary.copy(alpha = 0.40f),
                spotColor = BrandPrimary.copy(alpha = 0.50f),
            )
            .clip(shape)
            // Exact onboarding/auth CTA treatment: one calm high-contrast pill,
            // not a separate signed-in gradient vocabulary.
            .background(if (enabled) PrimaryButtonFill else PrimaryButtonDisabledFill)
            .heightIn(min = 56.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
            ) { Haptics.soft(0.6f); onClick() }
            .padding(horizontal = 28.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) PrimaryButtonInk else Ink,
        )
    }
}

/** Text field in the app's language — rounded, a faint glass fill, and a lavender
 * focus border/label. Replaces the default Material OutlinedTextField. */
@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    placeholderText: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = if (label.isNotBlank()) ({ Text(label) }) else null,
        placeholder = placeholderText?.let { { Text(it) } },
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(Radius.field),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Periwinkle,
            unfocusedBorderColor = LineStroke,
            focusedContainerColor = FieldFill,
            unfocusedContainerColor = FieldFill,
            cursorColor = Periwinkle,
            focusedLabelColor = Periwinkle,
            unfocusedLabelColor = TextMuted,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
    )
}

/** Selectable pill — filled lavender + dark text when chosen, a soft glassy
 * outline otherwise. Replaces the low-contrast Material FilterChip. */
@Composable
internal fun PickChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.round)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The fill/border/text cross-fade on selection instead of snapping — the chip
    // feels like it lights up rather than flicking to a new state.
    // Selected fill/text are themed: white pill + Ink on Night (as before); on
    // Dawn a white chip vanishes on cream, so it inverts to an Ink pill + white.
    val bg by animateColorAsState(
        if (selected) ChipSelectedFill else ChipFill, tween(220), label = "chipBg",
    )
    val border by animateColorAsState(
        if (selected) ChipSelectedFill else LineStroke, tween(220), label = "chipBorder",
    )
    val fg by animateColorAsState(if (selected) ChipSelectedInk else TextSoft, tween(220), label = "chipFg")
    val isSelected = selected
    Box(
        Modifier
            .heightIn(min = 48.dp)   // a11y: >= 48dp touch target
            .pressScale(pressed)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                // Announces as a selectable control, not an unlabelled tappable Box —
                // TalkBack now reads "Anxious, selected" instead of just "Anxious".
                role = Role.Button,
            ) { Haptics.selection(); onClick() }
            // `this.` is load-bearing: a bare `selected` would bind to PickChip's
            // own `selected` parameter (a val), not the semantics property.
            .semantics { this.selected = isSelected }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = fg,
        )
    }
}

/** Brand-tinted switch — lavender when on, glassy grey when off — so toggles match
 * the design system instead of the unconfigured Material default colours. */
@Composable
internal fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = SwitchThumbOn,   // Ink on Night, white on Dawn's deep track
            checkedTrackColor = Periwinkle,
            checkedBorderColor = Periwinkle,
            uncheckedThumbColor = TextSoft,
            uncheckedTrackColor = VeilSoft,
            uncheckedBorderColor = LineStroke,
        ),
    )
}

/** Destructive CTA — same pill geometry as [PrimaryButton] but a Danger-tinted fill,
 * so irreversible actions (delete account, delete forever) read as dangerous. */
@Composable
internal fun DangerButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(Danger, Danger))
    } else {
        Brush.horizontalGradient(listOf(Danger.copy(alpha = 0.28f), Danger.copy(alpha = 0.28f)))
    }
    Box(
        modifier
            .pressScale(pressed, down = 0.97f)
            .clip(RoundedCornerShape(26.dp))
            .background(brush)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { Haptics.warning(); onClick() }
            .padding(horizontal = 28.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) OnDanger else TextMuted2,
        )
    }
}

/** A shimmering skeleton placeholder for content that's loading — a soft fill with
 * a highlight sweeping across it (holds a static fill under Reduce Motion). Mirrors
 * the iOS Shimmer loader; use in place of a bare "Loading…" line. */
@Composable
internal fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val reduceMotion = rememberReduceMotion()
    val base = modifier.clip(shape).background(VeilSoft)
    if (reduceMotion) {
        Box(base)
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sweep",
    )
    Box(
        base.drawWithContent {
            drawContent()
            val sweepWidth = size.width * 0.6f
            val startX = x * size.width
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, ShimmerHighlight, Color.Transparent),
                    startX = startX,
                    endX = startX + sweepWidth,
                ),
            )
        },
    )
}

/** Credibility footer (REDESIGN §2.4): a quiet, expandable provenance line for
 * tools and programs. Collapsed it's one tappable line with a chevron; expanded
 * it shows the honest one-line "why this works" copy. No animation — a calm,
 * discrete state swap that's the same with or without Reduce Motion. */
@Composable
internal fun WhyThisWorks(text: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)   // a11y: >= 48dp touch target
            .clip(shape)
            .background(CardFill)
            .border(1.dp, LineStroke, shape)
            .clickable { open = !open }
            .padding(horizontal = cardPadding(), vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.common_why_this_works), style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (open) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        if (open) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}
