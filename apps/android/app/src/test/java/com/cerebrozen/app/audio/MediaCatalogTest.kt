package com.cerebrozen.app.audio

import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The catalogue contract, and the one property everything else rests on:
 * **a key the server has no bytes for must resolve to "", not to a broken URL** —
 * "" is what routes the caller to its bundled loop or synthesized tone. If this
 * ever returns a half-resolved path instead, every un-uploaded sound in the app
 * goes from "works, using the local fallback" to "silently fails to load".
 */
class MediaCatalogTest {

    private val base = "https://api.cerebrozen.in"

    @Before
    fun reset() = MediaCatalog.clear()

    @After
    fun tearDown() = MediaCatalog.clear()

    private fun rows(vararg json: String) = JSONArray("[${json.joinToString(",")}]")

    private fun row(key: String, url: String = "", loop: Boolean = false) =
        """{"key":"$key","kind":"game","url":"$url","loop":$loop}"""

    // ── the empty-url contract ──────────────────────────────────────────
    @Test
    fun a_key_with_no_server_asset_resolves_to_empty_so_the_caller_falls_back() {
        MediaCatalog.load(rows(row("game.bloom", url = "")), base)

        assertEquals("", MediaCatalog.urlFor("game.bloom"))
        assertFalse(MediaCatalog.has("game.bloom"))
    }

    @Test
    fun an_unknown_key_resolves_to_empty_rather_than_throwing() {
        MediaCatalog.load(rows(row("game.bloom")), base)

        assertEquals("", MediaCatalog.urlFor("game.nonexistent"))
        assertFalse(MediaCatalog.has("game.nonexistent"))
    }

    @Test
    fun an_empty_catalogue_leaves_every_key_on_its_fallback() {
        MediaCatalog.load(JSONArray(), base)

        assertTrue(MediaCatalog.loaded)   // fetched…
        assertEquals("", MediaCatalog.urlFor(MediaCatalog.Keys.AMBIENCE_RAIN))   // …and empty
    }

    // ── url resolution (delegates to MediaUrls — same rule as narration) ─
    @Test
    fun relative_urls_resolve_against_the_api_base() {
        MediaCatalog.load(rows(row("ambience.rain", url = "/media/assets/ambience.rain.m4a")), base)

        assertEquals(
            "https://api.cerebrozen.in/media/assets/ambience.rain.m4a",
            MediaCatalog.urlFor("ambience.rain"),
        )
        assertTrue(MediaCatalog.has("ambience.rain"))
    }

    @Test
    fun absolute_urls_pass_through_untouched() {
        MediaCatalog.load(rows(row("scene.dawn", url = "https://cdn.example.com/dawn.mp4")), base)

        assertEquals("https://cdn.example.com/dawn.mp4", MediaCatalog.urlFor("scene.dawn"))
    }

    // ── loop flag ───────────────────────────────────────────────────────
    @Test
    fun loop_flag_is_carried_per_key() {
        MediaCatalog.load(
            rows(
                row("ambience.rain", url = "/media/assets/r.m4a", loop = true),
                row("game.ripple", url = "/media/assets/g.m4a", loop = false),
            ),
            base,
        )

        assertTrue(MediaCatalog.isLooping("ambience.rain"))
        assertFalse(MediaCatalog.isLooping("game.ripple"))
        assertFalse(MediaCatalog.isLooping("key.never.seen"))
    }

    // ── reload replaces, never merges ───────────────────────────────────
    @Test
    fun reloading_drops_keys_the_server_no_longer_serves() {
        MediaCatalog.load(rows(row("game.bloom", url = "/media/assets/b.m4a")), base)
        assertTrue(MediaCatalog.has("game.bloom"))

        // An admin unpublished it — the client must forget the URL, not keep
        // pointing at an asset that may have been deleted from disk.
        MediaCatalog.load(JSONArray(), base)
        assertFalse(MediaCatalog.has("game.bloom"))
    }

    // ── malformed rows are skipped, not fatal ───────────────────────────
    @Test
    fun rows_without_a_key_are_skipped_and_the_rest_still_load() {
        MediaCatalog.load(
            JSONArray("""[{"kind":"game"},{"key":"","url":"/x"},${row("game.ripple", "/media/assets/g.m4a")}]"""),
            base,
        )

        assertTrue(MediaCatalog.has("game.ripple"))
    }

    // ── keys ────────────────────────────────────────────────────────────
    @Test
    fun pad_keys_are_indexed() {
        assertEquals("game.pad.0", MediaCatalog.Keys.gamePad(0))
        assertEquals("game.pad.3", MediaCatalog.Keys.gamePad(3))
    }

    @Test
    fun loaded_is_false_until_a_catalogue_is_parsed() {
        assertFalse(MediaCatalog.loaded)
        MediaCatalog.load(JSONArray(), base)
        assertTrue(MediaCatalog.loaded)
    }
}
