package com.cerebrozen.app.audio

import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The looping-bed fallback rule, which is what keeps the app audible with no server:
 * a bed plays its uploaded asset when the catalogue has one, and its bundled raw
 * resource otherwise. Get this backwards and a first launch (or an offline one, or
 * a server with nothing uploaded — i.e. today) plays silence instead of rain.
 */
class AmbientSourceTest {

    private val pkg = "com.cerebrozen.app"
    private val base = "https://api.cerebrozen.in"
    private val rawRes = 2131623936   // a stand-in R.raw id; the rule doesn't inspect it

    @Before
    fun reset() = MediaCatalog.clear()

    @After
    fun tearDown() = MediaCatalog.clear()

    @Test
    fun with_no_catalogue_at_all_a_bed_plays_its_bundled_resource() {
        assertEquals(
            "android.resource://com.cerebrozen.app/$rawRes",
            ambientUri(pkg, MediaCatalog.Keys.AMBIENCE_RAIN, rawRes),
        )
    }

    @Test
    fun a_catalogue_key_with_no_upload_still_plays_the_bundled_resource() {
        MediaCatalog.load(
            JSONArray("""[{"key":"ambience.rain","kind":"ambience","url":"","loop":true}]"""),
            base,
        )

        assertEquals(
            "android.resource://com.cerebrozen.app/$rawRes",
            ambientUri(pkg, MediaCatalog.Keys.AMBIENCE_RAIN, rawRes),
        )
    }

    @Test
    fun an_uploaded_asset_supersedes_the_bundled_resource() {
        MediaCatalog.load(
            JSONArray(
                """[{"key":"ambience.rain","kind":"ambience","url":"/media/assets/ambience.rain.m4a","loop":true}]""",
            ),
            base,
        )

        assertEquals(
            "https://api.cerebrozen.in/media/assets/ambience.rain.m4a",
            ambientUri(pkg, MediaCatalog.Keys.AMBIENCE_RAIN, rawRes),
        )
    }

    @Test
    fun an_unmapped_key_falls_back_rather_than_producing_a_broken_uri() {
        // ToolAmbience.keyFor returns "" for a resource with no catalogue key.
        assertEquals(
            "android.resource://com.cerebrozen.app/$rawRes",
            ambientUri(pkg, "", rawRes),
        )
    }
}
