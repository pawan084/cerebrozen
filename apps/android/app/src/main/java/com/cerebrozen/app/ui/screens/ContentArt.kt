package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
// Deliberately static (it's artwork, not ambience — no Reduce Motion branch
// needed) and deliberately theme-independent: like the HeroCard/game panels,
// content art stays deep night in both themes, so overlay text keeps the
// Cream/ArtTextSoft constants and the white top-light reads everywhere.

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
 */
@Composable
internal fun ContentArt(
    title: String,
    kind: String,
    modifier: Modifier = Modifier,
    motifScale: Float = 1f,
) {
    Canvas(modifier) { drawContentArt(title, kind, motifScale) }
}

/** The hero-sized variant: same art system, bigger motifs. Callers keep their
 * own scrim, so text contrast is untouched. */
@Composable
internal fun HeroArt(kind: String, title: String, modifier: Modifier = Modifier) =
    ContentArt(title = title, kind = kind, modifier = modifier, motifScale = 1.35f)

/** Modifier form for surfaces that paint art behind their own children
 * (FeaturedGameCard) — same drawing, drawn behind the content. */
internal fun Modifier.contentArtBackground(
    title: String,
    kind: String,
    motifScale: Float = 1.35f,
): Modifier = drawBehind { drawContentArt(title, kind, motifScale) }

private fun DrawScope.drawContentArt(title: String, kind: String, motifScale: Float) {
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
        "sleep" -> drawMoonMotif(seed, motifScale)
        "soundscape" -> drawWaveMotif(seed, motifScale)
        "meditation", "wind_down" -> drawRingsMotif(seed, motifScale)
        "program" -> drawDayDotsMotif(seed, motifScale)
        else -> drawOrbMotif(seed, motifScale)
    }
}

// ── Motifs — soft translucent white geometry, abstract and thin ─────────────

/** Sleep: a moon crescent in a soft halo + two small stars. */
private fun DrawScope.drawMoonMotif(seed: Float, m: Float) {
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
    drawStar(Offset(size.width * 0.28f, size.height * (0.20f + 0.10f * seed)), r * 0.30f, 0.45f)
    drawStar(Offset(size.width * 0.44f, size.height * 0.60f), r * 0.20f, 0.30f)
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

/** Soundscape: three layered sine waves, phase-shifted by the seed. */
private fun DrawScope.drawWaveMotif(seed: Float, m: Float) {
    val stroke = Stroke(width = (size.minDimension * 0.020f * m).coerceAtLeast(1f))
    listOf(0.40f to 0.24f, 0.55f to 0.16f, 0.70f to 0.10f).forEachIndexed { i, (yFrac, alpha) ->
        val amp = size.height * 0.055f * m
        val phase = (seed + i * 0.33f) * 2f * PI.toFloat()
        val baseY = size.height * yFrac
        val path = Path()
        val steps = 32
        for (s in 0..steps) {
            val x = size.width * s / steps
            val y = baseY + amp * sin(2f * PI.toFloat() * 1.6f * s / steps + phase)
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color.White.copy(alpha = alpha), style = stroke)
    }
}

/** Meditation / wind-down: concentric breathing rings around a still core. */
private fun DrawScope.drawRingsMotif(seed: Float, m: Float) {
    val c = Offset(size.width * (0.60f + 0.12f * seed), size.height * 0.42f)
    val base = size.minDimension * 0.14f * m
    val stroke = Stroke(width = (size.minDimension * 0.016f * m).coerceAtLeast(1f))
    listOf(1f to 0.30f, 1.7f to 0.17f, 2.4f to 0.09f).forEach { (k, alpha) ->
        drawCircle(Color.White.copy(alpha = alpha), radius = base * k, center = c, style = stroke)
    }
    drawCircle(Color.White.copy(alpha = 0.20f), radius = base * 0.30f, center = c)
}

/** Program: a rising diagonal path of day dots — the journey shape, filled to
 * a seed-stable day. Diagonal and corner-anchored on purpose: a horizontal
 * row centre-card would read as a (fake) progress bar next to the real one. */
private fun DrawScope.drawDayDotsMotif(seed: Float, m: Float) {
    val n = 5
    val r = size.minDimension * 0.045f * m
    val start = Offset(size.width * 0.52f, size.height * 0.48f)
    val end = Offset(size.width * 0.88f, size.height * (0.12f + 0.08f * seed))
    // A whisper of the path beneath the dots.
    drawLine(Color.White.copy(alpha = 0.10f), start, end, strokeWidth = (r * 0.30f).coerceAtLeast(1f))
    val filled = 1 + (seed * (n - 1)).toInt()   // 1..n-1 dots "done"
    for (i in 0 until n) {
        val t = i / (n - 1).toFloat()
        val c = Offset(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
        )
        if (i < filled) {
            drawCircle(Color.White.copy(alpha = 0.42f), radius = r, center = c)
        } else {
            drawCircle(
                Color.White.copy(alpha = 0.26f), radius = r, center = c,
                style = Stroke(width = (r * 0.35f).coerceAtLeast(1f)),
            )
        }
    }
}

/** Default: the brand orb — a soft radial glow with a thin halo ring. */
private fun DrawScope.drawOrbMotif(seed: Float, m: Float) {
    val c = Offset(size.width * (0.62f + 0.12f * seed), size.height * 0.40f)
    val r = size.minDimension * 0.30f * m
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.10f), Color.Transparent),
            center = c, radius = r,
        ),
        radius = r, center = c,
    )
    drawCircle(
        Color.White.copy(alpha = 0.18f), radius = r * 0.62f, center = c,
        style = Stroke(width = (size.minDimension * 0.012f * m).coerceAtLeast(1f)),
    )
}
