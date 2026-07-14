package com.cerebrozen.app.audio

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The narration-URL contract: relative "/media/…" values resolve against the
 * API base (trailing slashes tolerated), absolute URLs pass through, blanks
 * mean "no narration" — and the title registry [Player] reads follows the
 * same rule (unknown title → "" → ambient bed).
 */
class MediaUrlsTest {

    @Before
    fun reset() = MediaUrls.clear()

    // ── resolve ─────────────────────────────────────────────────────
    @Test
    fun resolve_prefixes_relative_paths_with_the_api_base() {
        assertEquals(
            "https://api.cerebrozen.in/media/narration/abc.mp3",
            MediaUrls.resolve("/media/narration/abc.mp3", "https://api.cerebrozen.in"),
        )
    }

    @Test
    fun resolve_tolerates_a_trailing_slash_on_the_base() {
        assertEquals(
            "https://api.cerebrozen.in/media/narration/abc.mp3",
            MediaUrls.resolve("/media/narration/abc.mp3", "https://api.cerebrozen.in/"),
        )
    }

    @Test
    fun resolve_passes_absolute_urls_through_verbatim() {
        assertEquals(
            "https://cdn.example.com/story.mp3",
            MediaUrls.resolve("https://cdn.example.com/story.mp3", "https://api.cerebrozen.in"),
        )
    }

    @Test
    fun resolve_maps_blank_and_null_to_empty() {
        assertEquals("", MediaUrls.resolve("", "https://api.cerebrozen.in"))
        assertEquals("", MediaUrls.resolve("   ", "https://api.cerebrozen.in"))
        assertEquals("", MediaUrls.resolve(null, "https://api.cerebrozen.in"))
    }

    // ── registry ────────────────────────────────────────────────────
    @Test
    fun registered_title_returns_its_url_and_unknown_returns_empty() {
        MediaUrls.register("Rain over quiet hills", "https://api/media/narration/x.mp3")
        assertEquals("https://api/media/narration/x.mp3", MediaUrls.urlFor("Rain over quiet hills"))
        assertEquals("", MediaUrls.urlFor("Deep night drift"))
    }

    @Test
    fun registering_blank_removes_a_stale_entry() {
        MediaUrls.register("Rain over quiet hills", "https://api/media/narration/x.mp3")
        MediaUrls.register("Rain over quiet hills", "")
        assertEquals("", MediaUrls.urlFor("Rain over quiet hills"))
    }
}
