package com.cerebrozen.app.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure screen-logic tests: the sleep time math (24h wrap, zero-padding), the
 * greeting buckets, and the JSON→model parsers. These are the bits most likely
 * to break silently on a schema tweak or an off-by-one.
 */
class ScreenLogicTest {

    // ── Sleep time math ─────────────────────────────────────────────
    @Test
    fun minutesToLabel_formats_hours_and_zero_padded_minutes() {
        assertEquals("7h 30m", minutesToLabel(450))
        assertEquals("8h 05m", minutesToLabel(485))
        assertEquals("0h 00m", minutesToLabel(0))
    }

    @Test
    fun hhmm_zero_pads_and_wraps_around_the_clock() {
        assertEquals("23:00", hhmm(23 * 60))
        assertEquals("07:05", hhmm(7 * 60 + 5))
        assertEquals("00:00", hhmm(24 * 60))   // exactly midnight wraps to 0
        assertEquals("23:30", hhmm(-30))        // −30m from midnight wraps back a day
    }

    // ── Greeting buckets ────────────────────────────────────────────
    @Test
    fun greeting_buckets_by_hour() {
        assertEquals("Good morning", greetingFor(5))
        assertEquals("Good morning", greetingFor(11))
        assertEquals("Good afternoon", greetingFor(12))
        assertEquals("Good afternoon", greetingFor(16))
        assertEquals("Good evening", greetingFor(17))
        assertEquals("Good evening", greetingFor(2))   // small hours
    }

    // ── Parsers (JSON → model) ──────────────────────────────────────
    @Test
    fun parseNights_maps_rows_and_defaults_missing_duration() {
        val rows = JSONArray()
            .put(JSONObject().put("date", "2026-07-04").put("duration_min", 445).put("quality", 4))
            .put(JSONObject().put("date", "2026-07-05").put("quality", 3))  // no duration_min
        val nights = parseNights(rows)
        assertEquals(2, nights.size)
        assertEquals(Night("2026-07-04", 445, 4), nights[0])
        assertEquals(0, nights[1].duration)   // optInt default
    }

    @Test
    fun parseChat_maps_role_and_text_in_order() {
        val rows = JSONArray()
            .put(JSONObject().put("role", "user").put("text", "hi"))
            .put(JSONObject().put("role", "assistant").put("text", "hello"))
        assertEquals(listOf(Msg("user", "hi"), Msg("assistant", "hello")), parseChat(rows))
    }

    @Test
    fun parseEntries_takes_date_prefix_and_defaults_risk() {
        val rows = JSONArray().put(
            JSONObject().put("title", "T").put("body", "B")
                .put("created_at", "2026-07-04T12:34:56Z"),   // no risk_level field
        )
        val entries = parseEntries(rows)
        assertEquals(1, entries.size)
        assertEquals("2026-07-04", entries[0].date)   // created_at.take(10)
        assertEquals("none", entries[0].risk)          // optString default
    }

    // ── Ref-batch pure logic ────────────────────────────────────────
    @Test
    fun fmtSession_renders_minutes_and_padded_seconds() {
        assertEquals("0:00", fmtSession(0))
        assertEquals("0:09", fmtSession(9))
        assertEquals("2:05", fmtSession(125))
    }

    @Test
    fun filterCatalogue_needs_two_chars_and_matches_title_subtitle_kind() {
        val pool = listOf(
            SearchItem("Rain over quiet hills", "Sleep story", "sleep", 18, ""),
            SearchItem("Ocean breathing", "Breathwork", "meditation", 5, ""),
        )
        assertEquals(0, filterCatalogue(pool, "r").size)
        assertEquals(listOf("Rain over quiet hills"), filterCatalogue(pool, "rain").map { it.title })
        assertEquals(listOf("Ocean breathing"), filterCatalogue(pool, "breathwork").map { it.title })
        assertEquals(listOf("Rain over quiet hills"), filterCatalogue(pool, "SLEEP").map { it.title })
    }

    @Test
    fun parsePlanSteps_sorts_by_order_and_reads_done() {
        val plan = org.json.JSONObject(
            """{"steps":[{"id":"b","title":"Second","detail":"","order":2,"done":true},
                         {"id":"a","title":"First","detail":"","order":1,"done":false}]}"""
        )
        val steps = parsePlanSteps(plan)
        assertEquals(listOf("First", "Second"), steps.map { it.title })
        assertEquals(listOf(false, true), steps.map { it.done })
    }

    @Test
    fun parsePatterns_reads_statement_and_basis() {
        val payload = org.json.JSONObject(
            """{"patterns":[{"statement":"Evenings are hardest.","basis":"6 of 8 check-ins"}]}"""
        )
        val learned = parsePatterns(payload)
        assertEquals(1, learned.size)
        assertEquals("Evenings are hardest.", learned[0].statement)
        assertEquals("6 of 8 check-ins", learned[0].basis)
    }

    // ── Streak milestones ───────────────────────────────────────────
    @Test
    fun milestoneLine_fires_only_on_milestone_days() {
        assertEquals("🎉 3-day milestone — beautifully done", milestoneLine(3))
        assertEquals("🎉 7-day milestone — beautifully done", milestoneLine(7))
        assertEquals(null, milestoneLine(0))
        assertEquals(null, milestoneLine(4))
        assertEquals(null, milestoneLine(15))
    }

    // ── Crisis suggestion detection (Talk banner) ───────────────────
    @Test
    fun hasCrisisSuggestion_detects_the_crisis_action() {
        val risky = JSONArray()
            .put(JSONObject().put("label", "Breathe").put("action", "breathing"))
            .put(JSONObject().put("label", "Get support").put("action", "crisis"))
        val calm = JSONArray().put(JSONObject().put("label", "Breathe").put("action", "breathing"))
        assertEquals(true, hasCrisisSuggestion(risky))
        assertEquals(false, hasCrisisSuggestion(calm))
        assertEquals(false, hasCrisisSuggestion(null))
        assertEquals(false, hasCrisisSuggestion(JSONArray()))
    }

    // ── DPDP consent notice (ConsentNotice.kt — cross-stack contract) ──
    @Test
    fun defaultNoticeCode_maps_app_language_and_keeps_hinglish_english() {
        assertEquals("hi", defaultNoticeCode("Hindi"))
        assertEquals("pa", defaultNoticeCode("Punjabi"))
        assertEquals("ta", defaultNoticeCode("Tamil"))
        assertEquals("en", defaultNoticeCode("Hinglish"))  // Latin script — English notice
        assertEquals("en", defaultNoticeCode("English"))
    }

    @Test
    fun noticeFor_falls_back_to_english_for_unknown_codes() {
        assertEquals("English", noticeFor("xx").nativeName)
        assertEquals("हिन्दी", noticeFor("hi").nativeName)
    }

    @Test
    fun every_notice_language_carries_all_six_consent_categories() {
        NOTICE_CODES.forEach { code ->
            val notice = noticeFor(code)
            CONSENT_KEY_ORDER.forEach { key ->
                val cat = notice.categories[key]
                assertEquals("$code/$key label present", false, cat?.label.isNullOrBlank())
                assertEquals("$code/$key hint present", false, cat?.hint.isNullOrBlank())
            }
        }
    }

    // ── Conversation starters + Talk transcript ─────────────────────
    @Test
    fun parseStarters_maps_topics_and_drops_blanks() {
        val payload = JSONObject().put(
            "topics",
            JSONArray()
                .put(JSONObject().put("id", "1").put("topic", "A worry that won't settle"))
                .put(JSONObject().put("id", "2").put("topic", ""))
                .put(JSONObject().put("id", "3").put("topic", "One small win today")),
        )
        assertEquals(listOf("A worry that won't settle", "One small win today"), parseStarters(payload))
        assertEquals(emptyList<String>(), parseStarters(JSONObject()))
    }

    @Test
    fun talkTranscript_labels_roles_and_takes_the_tail() {
        val messages = (1..10).map { Msg(if (it % 2 == 1) "user" else "assistant", "m$it") }
        val text = talkTranscript(messages, take = 2)
        assertEquals("Me: m9\n\nCereBro: m10", text)
    }

    // ── Oracle widgets (cross-stack widget kinds) ───────────────────
    @Test
    fun parseWidget_reads_kind_title_description_and_rejects_blank_kind() {
        val w = parseWidget(
            JSONObject().put("widget_kind", "breathing")
                .put("title", "2-minute breathing").put("description", "A guided breath."),
        )
        assertEquals(ChatWidget("breathing", "2-minute breathing", "A guided breath."), w)
        assertEquals(null, parseWidget(JSONObject().put("title", "No kind")))
        assertEquals(null, parseWidget(null))
    }

    @Test
    fun widgetRoute_maps_every_cross_stack_widget_kind_natively() {
        assertEquals("games", widgetRoute("breathing"))
        assertEquals("games", widgetRoute("grounding"))
        assertEquals("home", widgetRoute("mood_check"))
        assertEquals("journal", widgetRoute("mini_journal"))
        assertEquals("sleep", widgetRoute("sleep_checkin"))
        assertEquals("onegoodthing", widgetRoute("one_good_thing"))
        assertEquals("intention", widgetRoute("intention_set"))
        assertEquals("tipp", widgetRoute("dbt_skill"))
        assertEquals(null, widgetRoute("something_future"))   // unknown stays honest
    }

    // ── Journal search ──────────────────────────────────────────────
    @Test
    fun filterEntries_matches_title_or_body_case_insensitively() {
        val entries = listOf(
            Entry("Meeting pressure", "A bit stressed", "2026-07-01", "none"),
            Entry("Calm evening", "Slept WELL after tea", "2026-07-02", "none"),
        )
        assertEquals(listOf(entries[0]), filterEntries(entries, "PRESSURE"))
        assertEquals(listOf(entries[1]), filterEntries(entries, "slept well"))
        assertEquals(entries, filterEntries(entries, "  "))   // blank query = all
        assertEquals(emptyList<Entry>(), filterEntries(entries, "nope"))
    }

    // ── Games + local stores ────────────────────────────────────────
    private class FakeStore : com.cerebrozen.app.net.Session.Store {
        val m = mutableMapOf<String, String>()
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    private fun freshStore() = com.cerebrozen.app.net.Session
        .resetForTest(FakeStore()) { _, _, _, _, _ -> 200 to "{}" }

    @Test
    fun buildDeck_holds_exactly_two_of_each_emoji() {
        val deck = buildDeck(MEMORY_EMOJIS, kotlin.random.Random(7))
        assertEquals(MEMORY_EMOJIS.size * 2, deck.size)
        MEMORY_EMOJIS.forEach { e -> assertEquals(2, deck.count { it == e }) }
    }

    @Test
    fun flowerFor_is_deterministic_and_cycles_the_palette() {
        assertEquals(flowerFor(0), FLOWERS[0])
        assertEquals(flowerFor(FLOWERS.size), FLOWERS[0])   // wraps
        assertEquals(flowerFor(2), flowerFor(2))
    }

    @Test
    fun sleepFavs_toggle_round_trips_through_the_store() {
        freshStore()
        assertEquals(emptySet<String>(), SleepFavs.all())
        assertEquals(setOf("Rain over hills"), SleepFavs.toggle("Rain over hills"))
        assertEquals(setOf("Rain over hills"), SleepFavs.all())          // persisted
        assertEquals(emptySet<String>(), SleepFavs.toggle("Rain over hills"))  // off again
    }

    @Test
    fun gratitude_appends_trims_and_caps() {
        freshStore()
        assertEquals(listOf("Morning tea"), Gratitude.add("  Morning tea  "))
        assertEquals(listOf("Morning tea"), Gratitude.add("   "))   // blank ignored
        repeat(60) { Gratitude.add("thing $it") }
        assertEquals(50, Gratitude.all().size)                      // capped
        assertEquals("thing 59", Gratitude.all().last())
    }

    @Test
    fun baseline_saves_once_and_keeps_the_first_date() {
        freshStore()
        assertEquals(null, BaselineStore.get())
        BaselineStore.set(4, 2, "2026-07-07")
        assertEquals(Triple(4, 2, "2026-07-07"), BaselineStore.get())
        BaselineStore.set(1, 5, "2026-08-01")   // re-save: values move, date doesn't
        assertEquals(Triple(1, 5, "2026-07-07"), BaselineStore.get())
    }
}
