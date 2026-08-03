package com.cerebrozen.app.ui.screens

import androidx.compose.ui.graphics.toArgb
import com.cerebrozen.app.ui.theme.ArtPeriwinkle
import com.cerebrozen.app.ui.theme.ArtWarm
import com.cerebrozen.app.ui.theme.Teal
import com.cerebrozen.app.ui.theme.Violet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W21 generative artwork — the pure layer. The seed must be deterministic
 * (same title → same art, every run, every device) and well-spread (siblings
 * in a list should not cluster on one hue), and the gradient stops must be
 * kind-consistent but title-distinct.
 */
class ContentArtTest {

    @Test
    fun artSeed_isDeterministic() {
        listOf("Ocean Rain", "Midnight Train", "Body Scan", "Sleep Reset", "").forEach { title ->
            assertEquals("seed for \"$title\" must be stable", artSeed(title), artSeed(title), 0f)
        }
        // Regression pin: the fold+fmix32 contract is ours — a silent change
        // would redraw every tile in the app. ("" folds to 0, and fmix32(0)=0.)
        assertEquals(0f, artSeed(""), 0f)
    }

    @Test
    fun artSeed_staysInUnitRange() {
        (0 until 500).forEach { i ->
            val s = artSeed("Title $i")
            assertTrue("seed $s out of 0..1 for 'Title $i'", s in 0f..1f)
        }
    }

    @Test
    fun artSeed_isWellDistributed() {
        // 200 realistic-ish sibling titles must spread across the unit range:
        // most values distinct, and at least 8 of 10 deciles occupied.
        val seeds = (1..200).map { artSeed("Sleep Story $it") }
        assertTrue("too many collisions: ${seeds.distinct().size}", seeds.distinct().size >= 150)
        val deciles = seeds.map { (it * 10).toInt().coerceAtMost(9) }.distinct().size
        assertTrue("only $deciles/10 deciles occupied", deciles >= 8)
        assertTrue("seeds never reach the low range", seeds.min() < 0.15f)
        assertTrue("seeds never reach the high range", seeds.max() > 0.85f)
    }

    @Test
    fun artStops_areDeterministic_andTitleDistinct() {
        val a = artStops("Ocean Rain", "soundscape")
        val b = artStops("Ocean Rain", "soundscape")
        assertEquals(a.map { it.toArgb() }, b.map { it.toArgb() })
        // Two siblings of one kind share the family but never the exact hue.
        val other = artStops("Forest Night", "soundscape")
        assertNotEquals(a[0].toArgb(), other[0].toArgb())
    }

    @Test
    fun artAccents_mapKindsToTheirFamilies() {
        // The cross-kind accent contract (mirrors the spec: soundscape/sleep →
        // violet/blue night hues; meditation/wind_down → teal/cyan; program →
        // warm/rose; anything else → the periwinkle brand family).
        assertEquals(Violet, artAccent("soundscape"))
        assertEquals(Violet, artAccent("sleep"))
        assertEquals(Teal, artAccent("meditation"))
        assertEquals(Teal, artAccent("wind_down"))
        assertEquals(ArtWarm, artAccent("program"))
        assertEquals(ArtPeriwinkle, artAccent(""))
        assertEquals(ArtPeriwinkle, artAccent("journal"))
    }

    // ── Variety WITHIN a kind (the "three near-identical teal cards" problem) ──
    @Test
    fun artVariant_isDeterministicAndSpreadAcrossAllThree() {
        // The kind picks the motif family; this picks the arrangement. Without
        // it, every meditation drew the same concentric rings in the same place
        // and a rail of three read as one design printed three times.
        listOf("Body scan", "Morning calm", "Soft focus", "").forEach {
            assertEquals(artVariant(it), artVariant(it))
            assertTrue(artVariant(it) in 0..2)
        }
        val counts = IntArray(3)
        (0 until 300).forEach { counts[artVariant("Title $it")]++ }
        counts.forEach { assertTrue("every arrangement must get used: ${counts.toList()}", it > 60) }
    }

    @Test
    fun theSeededRailOfMeditationsNoLongerRepeatsItself() {
        // The exact case behind the complaint: three seeded meditations shown
        // together on Home. They must not share an arrangement.
        val rail = listOf("Body scan", "Morning calm", "Soft focus").map(::artVariant)
        assertEquals("siblings on one rail must differ", rail.size, rail.toSet().size)
    }

    @Test
    fun artVariant_isIndependentOfArtSeed() {
        // Composition and hue come from different hashes on purpose: two titles
        // landing on close hues should still be laid out differently.
        val sameVariant = (0 until 200).map { "T$it" }.groupBy(::artVariant)
        sameVariant.values.forEach { group ->
            val seeds = group.take(12).map(::artSeed)
            assertTrue("hue must still vary inside one arrangement", seeds.toSet().size > 6)
        }
    }

    @Test
    fun artHueShift_wandersWiderThanItUsedTo() {
        // Was 0.25..0.70; siblings separated by a few degrees, which reads as
        // "the same teal" at tile size.
        val shifts = (0 until 200).map { artHueShift("Title $it") }
        assertTrue(shifts.min() < 0.25f)
        assertTrue(shifts.max() > 0.70f)
        shifts.forEach { assertTrue(it in 0f..1f) }
    }
}
