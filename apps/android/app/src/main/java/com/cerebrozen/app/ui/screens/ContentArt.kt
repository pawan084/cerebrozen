package com.cerebrozen.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.cerebrozen.app.ui.theme.ArtCyan
import com.cerebrozen.app.ui.theme.ArtPeriwinkle
import com.cerebrozen.app.ui.theme.ArtScrim
import com.cerebrozen.app.ui.theme.ArtWarm
import com.cerebrozen.app.ui.theme.Teal
import com.cerebrozen.app.ui.theme.ThumbBlue
import com.cerebrozen.app.ui.theme.ThumbIndigo
import com.cerebrozen.app.ui.theme.ThumbRose
import com.cerebrozen.app.ui.theme.Violet
import kotlin.math.PI
import kotlin.math.sin

// ── W21: deterministic generative artwork ───────────────────────────────────
// Most served content ships without an image_url, and remote heroes die
// offline — so every thumbnail, hero and banner medallion can ALWAYS render
// designed art from nothing but (title, kind). The art is pure Canvas: a deep
// diagonal gradient in the kind's accent family, drifted per title so siblings
// look related but never identical, plus one calm abstract motif per kind.
// Tiles stay deliberately static (artwork, not ambience). W24 adds an opt-in
// [alive] mode for HERO surfaces only: one imperceptibly slow 22s loop drives
// a wave phase-drift, star twinkle, ±2% ring breath, a sequential day-dot glow
// pass and a ±3% orb-glow breath — ambience where the art IS the content.
// Reduce Motion renders exactly the static art. Theme-independent throughout:
// like the HeroCard/game panels, content art stays deep night in both themes,
// so overlay text keeps the Cream/ArtTextSoft constants and the white
// top-light reads everywhere.

/** Stable per-title seed in 0..1 — the same title always gets the same art,
 * on every device and every run (a hand-rolled fold, not `hashCode`, so the
 * contract is ours). Drives the gradient hue drift + motif placement. */
internal fun artSeed(title: String): Float {
    var h = 0
    for (c in title) h = 31 * h + c.code
    // murmur3's fmix32 avalanche: sibling titles ("Sleep Story 1/2/3…") differ
    // by tiny fold deltas and would otherwise cluster on one hue; this spreads
    // them across the whole range (distribution is unit-tested).
    h = h xor (h ushr 16)
    h *= 0x85ebca6b.toInt()
    h = h xor (h ushr 13)
    h *= 0xc2b2ae35.toInt()
    h = h xor (h ushr 16)
    return ((h and 0x7fffffff) % 997) / 996f
}

/** The kind's accent family — the two hues its gradients blend between.
 * Partners are deliberately a few hue-degrees apart so the per-title drift
 * has real room (Teal's partner is the deeper cyan-blue, not its twin). */
internal fun artAccents(kind: String): Pair<Color, Color> = when (kind) {
    "soundscape", "sleep" -> Violet to ThumbBlue
    "meditation", "wind_down" -> Teal to ThumbBlue
    "program" -> ArtWarm to ThumbRose
    else -> ArtPeriwinkle to ThumbIndigo
}

/** The single accent a kind contributes to surrounding chrome (banner wash). */
internal fun artAccent(kind: String): Color = artAccents(kind).first

/** The three diagonal-gradient stops for a piece: a clearly-hued top-left
 * settling through a deep mid into a near-night floor. The per-title seed
 * rotates the hue inside the family and nudges the depth, so every title is
 * distinct while siblings of one kind stay recognisably related. Pure. */
internal fun artStops(title: String, kind: String): List<Color> {
    val (a, b) = artAccents(kind)
    val seed = artSeed(title)
    // Wander well inside the family so siblings are clearly distinct hues, and
    // keep the lead stop rich (light darkening) — muddiness kills the art.
    val lead = lerp(a, b, 0.25f + 0.45f * seed)
    val mid = lerp(b, a, 0.35f * (1f - seed))
    return listOf(
        lerp(lead, ArtScrim, 0.22f + 0.10f * seed),
        lerp(mid, ArtScrim, 0.60f),
        lerp(lead, ArtScrim, 0.85f),
    )
}

/**
 * Designed artwork for a content tile — always renders, no network. Use as
 * the base layer under an AsyncImage so a blank/failed image_url degrades to
 * art instead of a flat slab. [motifScale] grows the motif for hero surfaces.
 * [alive] (heroes only — tiles stay static) runs the W24 living-art loop:
 * one 22s linear phase, calm because it is imperceptibly slow; Reduce Motion
 * holds phase 0, which is exactly today's static art.
 */
@Composable
internal fun ContentArt(
    title: String,
    kind: String,
    modifier: Modifier = Modifier,
    motifScale: Float = 1f,
    alive: Boolean = false,
) {
    val live = alive && !rememberReduceMotion()
    val phase: Float? = if (live) {
        val transition = rememberInfiniteTransition(label = "art-alive")
        val p by transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Restart),
            label = "art-phase",
        )
        p
    } else {
        null   // static — tiles, and every alive surface under Reduce Motion
    }
    Canvas(modifier) { drawContentArt(title, kind, motifScale, phase) }
}

/** The hero-sized variant: same art system, bigger motifs. Callers keep their
 * own scrim, so text contrast is untouched. [alive] opts a hero into the W24
 * living-art loop (heroes only, never tiles). */
@Composable
internal fun HeroArt(kind: String, title: String, modifier: Modifier = Modifier, alive: Boolean = false) =
    ContentArt(title = title, kind = kind, modifier = modifier, motifScale = 1.35f, alive = alive)

/** Modifier form for surfaces that paint art behind their own children
 * (FeaturedGameCard) — same drawing, drawn behind the content. */
internal fun Modifier.contentArtBackground(
    title: String,
    kind: String,
    motifScale: Float = 1.35f,
): Modifier = drawBehind { drawContentArt(title, kind, motifScale) }

// The living-art loop, expressed as pure phase curves (phase ∈ 0..1 over 22s,
// RepeatMode.Restart; null = static art, byte-identical to pre-W24) — every
// term uses an INTEGER number of sine cycles so the wrap at 1→0 is seamless.
private const val TAU = 2f * PI.toFloat()

/** Star twinkle: alpha factor 0.5→1, three ~7.3s cycles per loop, offset per star. */
private fun twinkle(phase: Float?, offset: Float): Float =
    if (phase == null) 1f else 0.75f + 0.25f * sin(TAU * (3f * phase + offset))

/** Breathing-ring scale: ±2%, two ~11s cycles per loop, offset per ring. */
private fun ringBreath(phase: Float?, offset: Float): Float =
    if (phase == null) 1f else 1f + 0.02f * sin(TAU * (2f * phase + offset))

/** Orb-glow breath: ±3%, two ~11s cycles per loop. */
private fun orbBreath(phase: Float?): Float =
    if (phase == null) 1f else 1f + 0.03f * sin(TAU * 2f * phase)

private fun DrawScope.drawContentArt(title: String, kind: String, motifScale: Float, phase: Float? = null) {
    // Base: the kind-family diagonal gradient, drifted per title.
    drawRect(
        Brush.linearGradient(
            artStops(title, kind),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
    // A subtle top-light — white 8% radial at top-left. Constant: the art base
    // is always deep, so this reads on both themes.
    drawRect(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
            center = Offset.Zero,
            radius = maxOf(size.width, size.height) * 0.9f,
        ),
    )
    val seed = artSeed(title)
    when (kind) {
        "sleep" -> {
            drawMoonMotif(seed, motifScale, phase)
            drawSceneOverlay(title, seed)   // content-aware character on the night base
        }
        "soundscape" -> drawWaveMotif(seed, motifScale, phase)
        "meditation", "wind_down" -> drawRingsMotif(seed, motifScale, phase)
        "program" -> drawDayDotsMotif(seed, motifScale, phase)
        else -> drawOrbMotif(seed, motifScale, phase)
    }
}

// ── Motifs — soft translucent white geometry, abstract and thin ─────────────

/** Sleep: a moon crescent in a soft halo + two small stars (alive: the stars
 * twinkle 0.5→1 of their resting alpha on long, offset periods). */
private fun DrawScope.drawMoonMotif(seed: Float, m: Float, phase: Float? = null) {
    val r = size.minDimension * 0.17f * m
    val c = Offset(size.width * (0.66f + 0.10f * seed), size.height * 0.34f)
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
            center = c, radius = r * 2.4f,
        ),
        radius = r * 2.4f, center = c,
    )
    val moon = Path().apply { addOval(Rect(c - Offset(r, r), c + Offset(r, r))) }
    val biteCenter = c + Offset(r * 0.58f, -r * 0.30f)
    val biteR = r * 0.84f
    val bite = Path().apply {
        addOval(Rect(biteCenter - Offset(biteR, biteR), biteCenter + Offset(biteR, biteR)))
    }
    drawPath(Path.combine(PathOperation.Difference, moon, bite), Color.White.copy(alpha = 0.32f))
    drawStar(
        Offset(size.width * 0.28f, size.height * (0.20f + 0.10f * seed)),
        r * 0.30f, 0.45f * twinkle(phase, 0.15f),
    )
    drawStar(Offset(size.width * 0.44f, size.height * 0.60f), r * 0.20f, 0.30f * twinkle(phase, 0.62f))
}

/** A tiny four-point sparkle (concave diamond) — abstract, not clip-art. */
private fun DrawScope.drawStar(center: Offset, r: Float, alpha: Float) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(p, Color.White.copy(alpha = alpha))
}

/** Soundscape: three layered sine waves, phase-shifted by the seed (alive:
 * the whole set drifts one full wavelength per 22s loop — near-imperceptible;
 * alternate layers drift opposite ways so the water shimmers, not scrolls). */
private fun DrawScope.drawWaveMotif(seed: Float, m: Float, phase: Float? = null) {
    val stroke = Stroke(width = (size.minDimension * 0.020f * m).coerceAtLeast(1f))
    val drift = (phase ?: 0f) * TAU
    listOf(0.40f to 0.24f, 0.55f to 0.16f, 0.70f to 0.10f).forEachIndexed { i, (yFrac, alpha) ->
        val amp = size.height * 0.055f * m
        val wavePhase = (seed + i * 0.33f) * TAU + (if (i % 2 == 0) drift else -drift)
        val baseY = size.height * yFrac
        val path = Path()
        val steps = 32
        for (s in 0..steps) {
            val x = size.width * s / steps
            val y = baseY + amp * sin(TAU * 1.6f * s / steps + wavePhase)
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color.White.copy(alpha = alpha), style = stroke)
    }
}

/** Meditation / wind-down: concentric breathing rings around a still core
 * (alive: each ring swells ±2% on a slow offset breath; the core stays still). */
private fun DrawScope.drawRingsMotif(seed: Float, m: Float, phase: Float? = null) {
    val c = Offset(size.width * (0.60f + 0.12f * seed), size.height * 0.42f)
    val base = size.minDimension * 0.14f * m
    val stroke = Stroke(width = (size.minDimension * 0.016f * m).coerceAtLeast(1f))
    listOf(1f to 0.30f, 1.7f to 0.17f, 2.4f to 0.09f).forEachIndexed { i, (k, alpha) ->
        drawCircle(
            Color.White.copy(alpha = alpha),
            radius = base * k * ringBreath(phase, i * 0.21f),
            center = c, style = stroke,
        )
    }
    drawCircle(Color.White.copy(alpha = 0.20f), radius = base * 0.30f, center = c)
}

/** Program: a rising diagonal path of day dots — the journey shape, filled to
 * a seed-stable day. Diagonal and corner-anchored on purpose: a horizontal
 * row centre-card would read as a (fake) progress bar next to the real one. */
private fun DrawScope.drawDayDotsMotif(seed: Float, m: Float, phase: Float? = null) {
    val n = 5
    val r = size.minDimension * 0.045f * m
    val start = Offset(size.width * 0.52f, size.height * 0.48f)
    val end = Offset(size.width * 0.88f, size.height * (0.12f + 0.08f * seed))
    // A whisper of the path beneath the dots.
    drawLine(Color.White.copy(alpha = 0.10f), start, end, strokeWidth = (r * 0.30f).coerceAtLeast(1f))
    val filled = 1 + (seed * (n - 1)).toInt()   // 1..n-1 dots "done"
    // Alive: one slow glow pass walks the path per 22s loop — the pass enters
    // at -1 and leaves past n, so the loop's wrap happens fully off-path.
    val glowPos = phase?.let { it * (n + 2f) - 1f }
    for (i in 0 until n) {
        val t = i / (n - 1).toFloat()
        val c = Offset(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
        )
        val glow = glowPos?.let { (1f - kotlin.math.abs(it - i)).coerceAtLeast(0f) } ?: 0f
        if (glow > 0f) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.20f * glow), Color.Transparent),
                    center = c, radius = r * 2.6f,
                ),
                radius = r * 2.6f, center = c,
            )
        }
        if (i < filled) {
            drawCircle(Color.White.copy(alpha = 0.42f + 0.10f * glow), radius = r, center = c)
        } else {
            drawCircle(
                Color.White.copy(alpha = 0.26f + 0.10f * glow), radius = r, center = c,
                style = Stroke(width = (r * 0.35f).coerceAtLeast(1f)),
            )
        }
    }
}

/** Default: the brand orb — a soft radial glow with a thin halo ring (alive:
 * the glow breathes ±3% on a slow cycle; the halo ring holds still). */
private fun DrawScope.drawOrbMotif(seed: Float, m: Float, phase: Float? = null) {
    val c = Offset(size.width * (0.62f + 0.12f * seed), size.height * 0.40f)
    val r = size.minDimension * 0.30f * m * orbBreath(phase)
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.10f), Color.Transparent),
            center = c, radius = r,
        ),
        radius = r, center = c,
    )
    drawCircle(
        Color.White.copy(alpha = 0.18f), radius = size.minDimension * 0.30f * m * 0.62f, center = c,
        style = Stroke(width = (size.minDimension * 0.012f * m).coerceAtLeast(1f)),
    )
}

// ── Scene overlays: content-aware character on top of the night/moon base, keyed
// off the scene's OWN words, so "Gentle Rain" reads as rain and "Snowfall" as snow
// instead of every sleep tile being an identical moon. Same house style: soft,
// translucent white, abstract — never clip-art. Deterministic (seed-placed), static.

private enum class Scene { RAIN, SNOW, STORM, FOREST, NONE }

private fun sceneOf(title: String): Scene {
    val t = title.lowercase()
    return when {
        "snow" in t -> Scene.SNOW
        "thunder" in t || "storm" in t -> Scene.STORM
        "rain" in t || "cabin" in t -> Scene.RAIN
        "forest" in t || "meadow" in t -> Scene.FOREST
        else -> Scene.NONE
    }
}

/** Deterministic 0..1 stream from a seed + index — abstract particle placement. */
private fun jitter(seed: Float, i: Int, salt: Float): Float =
    ((seed * salt + i * 0.61803398f) % 1f).let { if (it < 0f) it + 1f else it }

private fun DrawScope.drawSceneOverlay(title: String, seed: Float) {
    when (sceneOf(title)) {
        Scene.RAIN -> drawRain(seed, 0.16f)
        Scene.STORM -> { drawCloud(seed); drawRain(seed, 0.13f) }
        Scene.SNOW -> drawSnow(seed)
        Scene.FOREST -> drawForest(seed)
        Scene.NONE -> Unit
    }
}

/** Thin slanted streaks scattered across the tile. */
private fun DrawScope.drawRain(seed: Float, alpha: Float) {
    val len = size.height * 0.11f
    val slant = size.width * 0.045f
    val w = (size.minDimension * 0.006f).coerceAtLeast(1f)
    for (i in 0 until 24) {
        val x = size.width * jitter(seed, i, 37f)
        val y = size.height * jitter(seed, i, 53f) * 0.9f
        drawLine(Color.White.copy(alpha = alpha), Offset(x, y), Offset(x - slant, y + len), strokeWidth = w)
    }
}

/** Soft scattered flakes of a couple of sizes. */
private fun DrawScope.drawSnow(seed: Float) {
    for (i in 0 until 28) {
        val r = size.minDimension * (0.006f + 0.008f * (i % 5) / 4f)
        drawCircle(
            Color.White.copy(alpha = 0.18f + 0.16f * (i % 4) / 3f),
            radius = r,
            center = Offset(size.width * jitter(seed, i, 41f), size.height * jitter(seed, i, 59f)),
        )
    }
}

/** A low row of soft triangle firs along the foot of the tile. */
private fun DrawScope.drawForest(seed: Float) {
    val n = 6
    val baseY = size.height * 0.94f
    val halfW = size.width * 0.06f
    for (i in 0 until n) {
        val cx = size.width * ((i + 0.5f) / n) + size.width * 0.05f * (seed - 0.5f)
        val h = size.height * (0.13f + 0.07f * ((i * 5 + (seed * 10).toInt()) % 3) / 2f)
        drawPath(
            Path().apply {
                moveTo(cx, baseY - h); lineTo(cx - halfW, baseY); lineTo(cx + halfW, baseY); close()
            },
            Color.White.copy(alpha = 0.12f),
        )
    }
}

/** A soft cloud (overlapping puffs) for storm scenes; rain is drawn beneath it. */
private fun DrawScope.drawCloud(seed: Float) {
    val cx = size.width * (0.30f + 0.12f * seed)
    val cy = size.height * 0.26f
    val r = size.minDimension * 0.11f
    listOf(-1f to 0f, 0f to -0.42f, 1f to 0f, 0.45f to 0.16f, -0.45f to 0.16f).forEach { (dx, dy) ->
        drawCircle(
            Color.White.copy(alpha = 0.13f),
            radius = r * (0.72f + 0.28f * (1f - kotlin.math.abs(dx))),
            center = Offset(cx + dx * r, cy + dy * r),
        )
    }
}
