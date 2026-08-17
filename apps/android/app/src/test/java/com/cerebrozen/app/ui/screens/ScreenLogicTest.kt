package com.cerebrozen.app.ui.screens

import androidx.compose.ui.text.input.KeyboardType
import com.cerebrozen.app.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure screen-logic tests: the sleep time math (24h wrap, zero-padding), the
 * greeting buckets, and the JSON→model parsers. These are the bits most likely
 * to break silently on a schema tweak or an off-by-one.
 */
class ScreenLogicTest {

    // ── Sleep time math ─────────────────────────────────────────────
    @Test
    fun hoursMinutes_splits_totals_for_the_localized_duration_format() {
        assertEquals(7 to 30, hoursMinutes(450))
        assertEquals(8 to 5, hoursMinutes(485))
        assertEquals(0 to 0, hoursMinutes(0))
    }

    @Test
    fun hhmm_zero_pads_and_wraps_around_the_clock() {
        assertEquals("23:00", hhmm(23 * 60))
        assertEquals("07:05", hhmm(7 * 60 + 5))
        assertEquals("00:00", hhmm(24 * 60))   // exactly midnight wraps to 0
        assertEquals("23:30", hhmm(-30))        // −30m from midnight wraps back a day
    }

    // ── Greeting buckets ────────────────────────────────────────────
    // greetingFor returns the string RESOURCE for the bucket (so the copy can
    // localize); the buckets themselves are what's under test.
    @Test
    fun greeting_buckets_by_hour() {
        assertEquals(R.string.today_greeting_morning, greetingFor(5))
        assertEquals(R.string.today_greeting_morning, greetingFor(11))
        assertEquals(R.string.today_greeting_afternoon, greetingFor(12))
        assertEquals(R.string.today_greeting_afternoon, greetingFor(16))
        assertEquals(R.string.today_greeting_evening, greetingFor(17))
        assertEquals(R.string.today_greeting_evening, greetingFor(2))   // small hours
    }

    // ── Parsers (JSON → model) ──────────────────────────────────────
    @Test
    fun parseNights_maps_rows_and_defaults_missing_duration() {
        val rows = JSONArray()
            .put(JSONObject().put("date", "2026-07-04").put("duration_min", 445).put("quality", 4))
            .put(JSONObject().put("date", "2026-07-05").put("quality", 3))  // no duration_min
        val nights = parseNights(rows)
        assertEquals(2, nights.size)
        assertEquals(SleepNight("2026-07-04", 445, 4), nights[0])
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
    fun parseTodayGuide_reads_title_and_body_and_stays_null_safe() {
        val enrolled = JSONObject(
            """{"title":"Sleep Reset","day":1,"days":7,
                "today_guide":{"title":"A steady wake time","body":"Pick a wake time you can keep."}}"""
        )
        assertEquals("A steady wake time" to "Pick a wake time you can keep.", parseTodayGuide(enrolled))
        // Older servers omit the field entirely (additive contract); a blank
        // guide and a missing program both stay null.
        assertEquals(null, parseTodayGuide(JSONObject("""{"title":"Legacy","day":2,"days":7}""")))
        assertEquals(null, parseTodayGuide(JSONObject("""{"today_guide":{"title":" ","body":""}}""")))
        assertEquals(null, parseTodayGuide(null))
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

    // ── Reduce Motion predicate (accessibility parity with iOS) ─────
    @Test
    fun reduceMotionFromScale_only_true_when_animations_are_off() {
        assertEquals(true, reduceMotionFromScale(0f))    // "Remove animations" on
        assertEquals(false, reduceMotionFromScale(1f))   // normal
        assertEquals(false, reduceMotionFromScale(0.5f)) // slowed, not removed
        assertEquals(false, reduceMotionFromScale(2f))   // sped up
    }

    // ── Presence milestones (REDESIGN §3.6 — counts showing up, never misses) ──
    @Test
    fun milestone_fires_only_on_milestone_days() {
        assertEquals(true, isMilestone(3))
        assertEquals(true, isMilestone(7))
        assertEquals(false, isMilestone(0))
        assertEquals(false, isMilestone(4))
        assertEquals(false, isMilestone(15))
    }

    // ── Home banner slot (W9) — priority order, time windows, dismissal ──
    @Test
    fun homeBanner_offline_outranks_everything() {
        assertEquals(HomeBanner.OFFLINE, homeBannerPriority(true, 8, false, emptySet(), true))
        assertEquals(HomeBanner.OFFLINE, homeBannerPriority(true, 22, true, emptySet(), false))
    }

    @Test
    fun homeBanner_morning_sleep_checkin_before_11_when_last_night_unlogged() {
        assertEquals(HomeBanner.SLEEP_CHECKIN, homeBannerPriority(false, 8, false, emptySet(), true))
        assertEquals(HomeBanner.SLEEP_CHECKIN, homeBannerPriority(false, 10, false, emptySet(), false))
        // 11:00 is past the morning window; a logged night never asks again.
        assertEquals(HomeBanner.PROGRAM, homeBannerPriority(false, 11, false, emptySet(), true))
        assertEquals(HomeBanner.NONE, homeBannerPriority(false, 8, true, emptySet(), false))
    }

    @Test
    fun homeBanner_dismissals_fall_through_to_the_next_banner() {
        assertEquals(HomeBanner.PROGRAM, homeBannerPriority(false, 8, false, setOf("sleep"), true))
        assertEquals(HomeBanner.NONE, homeBannerPriority(false, 8, false, setOf("sleep"), false))
        assertEquals(HomeBanner.PROGRAM, homeBannerPriority(false, 22, true, setOf("winddown"), true))
    }

    @Test
    fun homeBanner_evening_wind_down_from_21_unless_dismissed() {
        assertEquals(HomeBanner.WIND_DOWN, homeBannerPriority(false, 21, true, emptySet(), true))
        assertEquals(HomeBanner.WIND_DOWN, homeBannerPriority(false, 23, true, emptySet(), false))
        assertEquals(HomeBanner.NONE, homeBannerPriority(false, 20, true, emptySet(), false))  // 20:59 isn't evening yet
    }

    @Test
    fun homeBanner_program_strip_shows_while_enrolled_midday() {
        assertEquals(HomeBanner.PROGRAM, homeBannerPriority(false, 14, true, emptySet(), true))
        assertEquals(HomeBanner.NONE, homeBannerPriority(false, 14, true, emptySet(), false))
    }

    @Test
    fun hasLastNightLog_accepts_today_or_yesterday_and_ignores_junk() {
        val today = java.time.LocalDate.of(2026, 7, 11)
        assertEquals(true, hasLastNightLog(listOf("2026-07-11"), today))   // logged this morning
        assertEquals(true, hasLastNightLog(listOf("2026-07-10"), today))   // dated last evening
        assertEquals(false, hasLastNightLog(listOf("2026-07-08"), today))  // older nights don't count
        assertEquals(false, hasLastNightLog(listOf("not-a-date", ""), today))
        assertEquals(false, hasLastNightLog(emptyList(), today))
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
        assertEquals("breathing", widgetRoute("breathing"))
        assertEquals("ground", widgetRoute("grounding"))   // its own screen since 2026-08-03
        assertEquals("home", widgetRoute("mood_check"))
        assertEquals("journal", widgetRoute("mini_journal"))
        assertEquals("sleep", widgetRoute("sleep_checkin"))
        // Each one-field tool goes to its own prompt-led screen. Both used to be
        // sent to the bare Journal composer, which left `onegoodthing` and
        // `intention` registered in the graph with nothing anywhere able to open
        // them — and dropped each tool's prompt and its "why this works" footer.
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
        .resetForTest(FakeStore()) { _, _, _, _, _, _ -> 200 to "{}" }

    // ── Onboarding funnel progress (must not key off translated copy) ──
    @Test
    fun funnelProgress_climbs_with_the_step_not_the_language() {
        // The bug this replaced: the fraction matched the English eyebrow copy,
        // so on a Hindi device every step after Language fell through to 1f.
        // V2-c canonical order: five screens, then Today.
        val canonical = listOf(OStep.Welcome, OStep.Disclosure, OStep.Consent, OStep.State, OStep.Guest)
        val fractions = canonical.map { funnelProgress(it) }
        assertEquals(canonical.size, fractions.size)
        assertEquals(0f, funnelProgress(OStep.Welcome), 0.0001f)
        fractions.zipWithNext().forEach { (a, b) ->
            assertTrue("the bar never goes backwards", b > a)
        }
    }

    // ── Onboarding step numbering (the V2 four-step contract) ──
    @Test
    fun funnelStepIndex_follows_the_v2_contract() {
        assertEquals(0, funnelStepIndex(OStep.Welcome))   // outside the count
        assertEquals(1, funnelStepIndex(OStep.Disclosure))
        assertEquals(2, funnelStepIndex(OStep.Consent))
        assertEquals(3, funnelStepIndex(OStep.State))
        assertEquals(4, funnelStepIndex(OStep.Guest))
        assertEquals(4, funnelStepIndex(OStep.SignUp))    // account branch shares the slot
        val used = OStep.entries.map { funnelStepIndex(it) }
        assertTrue("all counted steps are represented", (1..ONBOARDING_STEPS).all { it in used })
        assertTrue("no step numbers past the declared total", used.all { it <= ONBOARDING_STEPS })
    }

    // ── V2-c defect pins (Audit L) ──
    @Test
    fun consent_defaults_are_all_false_and_cover_all_six_categories() {
        // "Nothing pre-ticked" has been silently reverted TWICE in this file's
        // history; this pin makes the third revert fail CI instead of shipping.
        val c = defaultConsent()
        assertEquals(
            setOf("mood_history", "ai_memory", "journal_memory", "sleep_history", "voice_storage", "model_training"),
            c.keys,
        )
        assertTrue("consent must be an action — no category may default on", c.values.none { it })
    }

    @Test
    fun state_tiles_write_the_mood_their_label_says() {
        // Audit L defect 3: "Clear · I feel steady" wrote mood "Anxious" as a
        // new profile's first data. The wire mood now matches the tile.
        assertEquals("Good", STATE_OPTIONS.first { it.id == "stressed" }.mood)
        assertEquals("Overwhelmed", STATE_OPTIONS.first { it.id == "distant" }.mood)
        // Every seeded mood stays inside the six-state cross-stack taxonomy.
        val taxonomy = setOf("Good", "Anxious", "Low", "Tired", "Overwhelmed", "Not sure")
        assertTrue(STATE_OPTIONS.all { it.mood in taxonomy })
    }

    @Test
    fun detectedLanguage_says_nothing_rather_than_guessing_english() {
        // "Detected on this device" under English on a Bengali phone is a small
        // lie on the first screen the user ever sees.
        assertEquals("English", detectedLanguageId("en"))
        assertEquals("Hindi", detectedLanguageId("hi"))
        assertEquals("Tamil", detectedLanguageId("TA"))   // case-insensitive
        assertNull(detectedLanguageId("bn"))
        assertNull(detectedLanguageId(""))
    }

    // ── Failure text a user is actually allowed to see ──
    @Test
    fun aNetworkFailureShowsOurWordsNotTheJvmS() {
        // Pulling the plug on the dev stack used to put "Failed to connect to
        // localhost/127.0.0.1:8000" on the Programs screen, because 19 call
        // sites did `it.message ?: fallback`. In the field that reads "Unable to
        // resolve host ..." — true, and meaningless to someone who lost signal.
        val fallback = "We couldn't reach the server."
        assertEquals(fallback, java.net.ConnectException("Failed to connect to localhost/127.0.0.1:8000").userMessage(fallback))
        assertEquals(fallback, java.net.UnknownHostException("api.cerebrozen.in").userMessage(fallback))
        assertEquals(fallback, org.json.JSONException("End of input at character 0").userMessage(fallback))
    }

    @Test
    fun theServersOwnDetailIsKeptBecauseItIsWrittenForPeople() {
        // Session.raw already curates ApiException's message from the server's
        // `detail` — including the free-tier cap and the rate limiter — so that
        // one must survive rather than be flattened into a generic line.
        val fallback = "generic"
        assertEquals(
            "You've used your 50 messages for today.",
            com.cerebrozen.app.net.Session.ApiException(429, "You've used your 50 messages for today.").userMessage(fallback),
        )
        // A blank server detail is no better than nothing — fall back.
        assertEquals(fallback, com.cerebrozen.app.net.Session.ApiException(500, "").userMessage(fallback))
    }

    // ── Journal polish (2026-08-04) ──
    @Test
    fun wordCount_counts_words_not_whitespace() {
        assertEquals(0, wordCount(""))
        assertEquals(0, wordCount("   "))
        assertEquals(1, wordCount("breathe"))
        assertEquals(4, wordCount("  a long   hard day\n"))
    }

    // ── Talk polish (2026-08-04): fresh start + moment-aware offers ──
    @Test
    fun startFresh_hides_older_messages_but_keeps_local_bubbles() {
        val msgs = listOf(
            Msg("user", "old", createdAt = "2026-08-01T10:00:00Z"),
            Msg("assistant", "old reply", createdAt = "2026-08-01T10:00:05Z"),
            Msg("user", "new", createdAt = "2026-08-04T09:00:00Z"),
            Msg("user", "local this session", createdAt = ""),
        )
        val visible = visibleAfterClear(msgs, "2026-08-03T00:00:00Z")
        assertEquals(listOf("new", "local this session"), visible.map { it.text })
        // No cleared stamp (or garbage) → everything shows.
        assertEquals(4, visibleAfterClear(msgs, null).size)
        assertEquals(4, visibleAfterClear(msgs, "not-a-date").size)
    }

    @Test
    fun tryTogether_orders_by_the_moment() {
        // Spiralling words put grounding first, whatever the hour.
        assertEquals(listOf("ground", "breathe", "reframe"), tryTogetherOrder(14, "my thoughts are racing"))
        // Late evening leads with breathing.
        assertEquals(listOf("breathe", "ground", "reframe"), tryTogetherOrder(22, "long day"))
        assertEquals(listOf("breathe", "ground", "reframe"), tryTogetherOrder(2, null))
        // An ordinary afternoon keeps the CBT reframe lead.
        assertEquals(listOf("reframe", "breathe", "ground"), tryTogetherOrder(14, "thinking about work"))
    }

    // ── Sounds 56-point wave (2026-08-04) ──
    @Test
    fun mixer_presets_align_with_layers_and_lead_with_just_rain() {
        val mixer = com.cerebrozen.app.audio.SoundscapeMixer
        // Every preset's vector is exactly one volume per layer.
        mixer.presets.forEach { assertEquals(mixer.layers.size, it.volumes.size) }
        // "Just rain" leads and matches the factory blend, so a first visit
        // names itself instead of reading "Custom mix".
        assertEquals("just_rain", mixer.presets.first().key)
        assertEquals(listOf(0.7f, 0f, 0f, 0f), mixer.presets.first().volumes)
    }

    @Test
    fun mixer_dominant_layer_names_the_loudest_audible_voice() {
        val mixer = com.cerebrozen.app.audio.SoundscapeMixer
        // Factory blend: rain leads.
        assertEquals(mixer.layers[0].nameRes, mixer.dominantLayerRes())
    }

    // ── Chat 52-point wave (2026-08-04) ──
    @Test
    fun phoneSpans_finds_helplines_and_ignores_breath_counts() {
        val text = "contact Tele-MANAS mental health support (14416) or Emergency services (112) right now."
        val found = phoneSpans(text).map { text.substring(it) }
        assertEquals(listOf("14416", "112"), found)
        // Breath pacing and clock times never become phone links.
        assertEquals(emptyList<String>(), phoneSpans("try 4-7-8 breathing at 9 pm").map { "x" })
        assertEquals(listOf("1800-599-0019"), phoneSpans("KIRAN 1800-599-0019").map { "KIRAN 1800-599-0019".substring(it) })
    }

    @Test
    fun stripMarkdownLite_neutralizes_llm_markup() {
        assertEquals("Take a slow breath.", stripMarkdownLite("Take a **slow** breath."))
        assertEquals("Take a slow breath.", stripMarkdownLite("Take a *slow* breath."))
        assertEquals("One step", stripMarkdownLite("## One step"))
        // Arithmetic and mid-word asterisks survive.
        assertEquals("4*5 is 20", stripMarkdownLite("4*5 is 20"))
    }

    @Test
    fun chipLabels_localize_known_wire_labels_only() {
        assertEquals(R.string.chip_urgent_support, chipLabelResFor("Urgent support"))
        assertEquals(R.string.chip_human, chipLabelResFor("Talk to a person"))
        assertNull(chipLabelResFor("Some future chip"))
    }

    // ── Trusted contact (the crisis surface's one editable thing) ──
    @Test
    fun aContactIsSavableOnceThereIsSomewhereToSendIt() {
        // Name stays optional on purpose: the escalation mail addresses the
        // person generically when it is blank, and demanding a name before
        // someone can nominate a lifeline is friction in the wrong place.
        assertEquals(true, trustedContactReady("someone@example.com"))
        assertEquals(true, trustedContactReady("+91 98765 43210"))
        assertEquals(false, trustedContactReady(""))
        assertEquals(false, trustedContactReady("   "))
    }

    @Test
    fun theKeyboardMatchesHowTheContactIsReached() {
        assertEquals(KeyboardType.Email, trustedKeyboard("email"))
        assertEquals(KeyboardType.Phone, trustedKeyboard("sms"))
        assertEquals(KeyboardType.Phone, trustedKeyboard("phone"))
        // An unknown method must not silently become an email keyboard.
        assertEquals(KeyboardType.Phone, trustedKeyboard("carrier-pigeon"))
    }

    @Test
    fun theValueFieldKnowsItsShapeBeforeTheBackendDoes() {
        // Email wants user@domain.tld; the client mirror is deliberately looser
        // than a full RFC parse — the backend stays the authority.
        assertEquals(true, trustedValueLooksValid("email", "someone@example.com"))
        assertEquals(false, trustedValueLooksValid("email", "someone@example"))
        assertEquals(false, trustedValueLooksValid("email", "someone.example.com"))
        assertEquals(false, trustedValueLooksValid("email", "so meone@example.com"))
        assertEquals(false, trustedValueLooksValid("email", ""))
        // Numbers allow phone punctuation and need at least seven digits.
        assertEquals(true, trustedValueLooksValid("phone", "+91 98765 43210"))
        assertEquals(true, trustedValueLooksValid("sms", "(020) 1234-567"))
        assertEquals(false, trustedValueLooksValid("phone", "12345"))
        assertEquals(false, trustedValueLooksValid("sms", "call me maybe"))
    }

    @Test
    fun theSavedCardReachesOutTheWayTheContactWasSaved() {
        assertEquals("tel:+919876543210", trustedReachUri("phone", "+919876543210"))
        assertEquals("smsto:+919876543210", trustedReachUri("sms", "+919876543210"))
        assertEquals("mailto:a@b.co", trustedReachUri("email", "a@b.co"))
        // Unknown methods fall back to mail — the only method that can't
        // misfire from a composer.
        assertEquals("mailto:a@b.co", trustedReachUri("carrier-pigeon", "a@b.co"))
    }

    // ── ContentList fallback (the offline copy of a section's advice) ──
    @Test
    fun aSectionWithNothingToShowFallsBackToItsOwnCopy() {
        // Sleep's wind-down guidance is why the slot exists. Its bundled CBT-I
        // stimulus-control advice used to render UNCONDITIONALLY beneath the
        // served list, so online users met the same two ideas twice within one
        // screen — once as a served guide, once as a card, with the same
        // citation printed under each.
        val empty = JSONArray()
        val one = JSONArray().put(JSONObject().put("title", "Dim the inputs"))

        assertEquals(ContentListState.Items, contentListState(null, one, hasFallback = true))
        assertEquals(ContentListState.Fallback, contentListState(null, empty, hasFallback = true))
        assertEquals(ContentListState.Fallback, contentListState("boom", null, hasFallback = true))

        // Without a fallback the caller still gets the honest empty/error line.
        assertEquals(ContentListState.Empty, contentListState(null, empty, hasFallback = false))
        assertEquals(ContentListState.Error, contentListState("boom", null, hasFallback = false))
    }

    @Test
    fun loadingNeverShowsTheFallbackFirst() {
        // A flash of the offline copy before the real list lands would be worse
        // than the shimmer — the user would read advice, then watch it replaced.
        assertEquals(ContentListState.Loading, contentListState(null, null, hasFallback = true))
        assertEquals(ContentListState.Loading, contentListState(null, null, hasFallback = false))
    }

    // ── Home content rail follows the same clock the theme does ──
    @Test
    fun theSmallHoursBelongToTheNightBefore() {
        // Seen on device at 00:09: the theme had gone Night for wind-down while
        // the rail offered "For this morning · Body scan" — a 10-minute
        // meditation, to someone still awake past midnight. The rail and the
        // theme now read the same clock.
        assertEquals("sleep", railKindFor(0).first)
        assertEquals("sleep", railKindFor(3).first)
        assertEquals("sleep", railKindFor(23).first)
        assertEquals("sleep", railKindFor(21).first)
        // Daytime is unchanged.
        assertEquals("meditation", railKindFor(9).first)
        assertEquals("soundscape", railKindFor(14).first)
        assertEquals("sleep", railKindFor(19).first)
        // 06:00 is the boundary: the night is over, the morning has started.
        assertEquals("meditation", railKindFor(6).first)
    }

    // ── Onboarding reminder options (every chip must mean something) ──
    // (V2-e: the everyReminderChipResolvesToARealChoice pin retired with the
    // NOTIFY machinery it pinned — the Notify step left the funnel and
    // Settings → Reminders owns scheduling with a real time picker.)

    // ── AI-disclosure cadence (re-show every 3h, across tab switches) ──
    @Test
    fun disclosureDue_only_after_the_full_interval() {
        val now = 1_000_000_000_000L
        assertEquals(false, disclosureDue(now, now))
        assertEquals(false, disclosureDue(now - DISCLOSURE_INTERVAL_MS + 1, now))
        assertEquals(true, disclosureDue(now - DISCLOSURE_INTERVAL_MS, now))
        assertEquals(true, disclosureDue(now - DISCLOSURE_INTERVAL_MS * 5, now))
    }

    @Test
    fun disclosureDue_treats_an_unset_or_backwards_clock_as_due() {
        val now = 1_000_000_000_000L
        assertEquals(true, disclosureDue(0L, now))
        assertEquals(true, disclosureDue(-1L, now))
        assertEquals(true, disclosureDue(now + 60_000, now))   // clock stepped back
    }

    // ── Breathe engine (one engine, three presets) ──────────────────
    @Test
    fun breathePhases_box_paces_four_beats_of_four() {
        val phases = breathePhases(BreathePreset.Box)
        // Asserted on the KIND, not the label resource: cues and haptics branch
        // on the enum, so that is the thing the pacing must get right.
        assertEquals(
            listOf(BreathKind.IN, BreathKind.HOLD, BreathKind.OUT, BreathKind.HOLD),
            phases.map { it.kind },
        )
        assertEquals(List(4) { 4 }, phases.map { it.seconds })
        assertEquals(listOf(true, true, false, false), phases.map { it.expanded })
        assertEquals(phases, breathePhases(BreathePreset.Color))   // Color shares the pacing
    }

    @Test
    fun breathePhases_reset_has_no_holds() {
        val phases = breathePhases(BreathePreset.Reset)
        assertEquals(listOf(BreathKind.IN, BreathKind.OUT), phases.map { it.kind })
        assertEquals(listOf(true, false), phases.map { it.expanded })
        // …and the exhale is the longer half (BreathePacingTest pins the numbers).
        assertTrue(phases[1].seconds > phases[0].seconds)
    }

    // ── Guided routines (wind-down ritual + guided imagery) ───────────────
    @Test
    fun nextPromptIndex_advances_then_ends_on_the_last_prompt() {
        assertEquals(1, nextPromptIndex(0, 6))
        assertEquals(5, nextPromptIndex(4, 6))
        assertNull("the last prompt hands over rather than wrapping", nextPromptIndex(5, 6))
        // Defensive: an index past the end must still end, not run backwards.
        assertNull(nextPromptIndex(9, 6))
        assertNull("an empty reel is already finished", nextPromptIndex(0, 0))
    }

    @Test
    fun ritualBlocks_have_unique_ids_and_real_durations() {
        assertEquals("ids are the persisted contract — a duplicate would collide in the pref",
            RITUAL_BLOCKS.size, RITUAL_BLOCKS.map { it.id }.distinct().size)
        assertTrue("every block claims at least a minute", RITUAL_BLOCKS.all { it.minutes >= 1 })
    }

    @Test
    fun sanitizeRitual_drops_unknown_ids_and_duplicates_keeping_order() {
        assertEquals(
            listOf("good", "settle"),
            sanitizeRitual(listOf("good", "affirmation", "settle", "good", "478")),
        )
        assertEquals(emptyList<String>(), sanitizeRitual(listOf("nope")))
    }

    @Test
    fun moveBlock_swaps_neighbours_and_refuses_to_fall_off_either_end() {
        val order = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveBlock(order, 1, -1))
        assertEquals(listOf("a", "c", "b"), moveBlock(order, 1, 1))
        assertEquals("moving the first up is a no-op, not a crash", order, moveBlock(order, 0, -1))
        assertEquals("moving the last down is a no-op, not a crash", order, moveBlock(order, 2, 1))
        assertEquals("an index that isn't in the list changes nothing", order, moveBlock(order, 7, -1))
    }

    @Test
    fun ritualMinutes_sums_only_blocks_that_exist() {
        val expected = RITUAL_BLOCKS.first { it.id == "good" }.minutes +
            RITUAL_BLOCKS.first { it.id == "settle" }.minutes
        assertEquals(expected, ritualMinutes(listOf("good", "settle")))
        assertEquals(0, ritualMinutes(emptyList()))
        assertEquals("an unknown id contributes nothing rather than throwing",
            0, ritualMinutes(listOf("affirmation")))
    }

    @Test
    fun ritualStore_round_trips_and_sanitizes_on_the_way_out() {
        freshStore()
        RitualStore.save(listOf("good", "affirmation", "good", "settle"), "  close my laptop  ")
        assertEquals(listOf("good", "settle"), RitualStore.blocks())
        assertEquals("close my laptop", RitualStore.cue())
        // Nothing saved yet on a fresh install must read as an empty ritual,
        // not a crash or a phantom block.
        freshStore()
        assertEquals(emptyList<String>(), RitualStore.blocks())
        assertEquals("", RitualStore.cue())
    }

    @Test
    fun ritualProgress_is_an_explicit_fraction_and_clamps() {
        assertEquals(0.25f, ritualProgress(0, 4), 0.001f)
        assertEquals(1f, ritualProgress(3, 4), 0.001f)
        assertEquals("past the end still reads as finished, never over-full",
            1f, ritualProgress(9, 4), 0.001f)
        assertEquals("no ritual is no progress, not a divide by zero",
            0f, ritualProgress(0, 0), 0.001f)
    }

    @Test
    fun breatheTint_shifts_only_for_the_color_preset_and_wraps() {
        assertEquals(breatheTint(BreathePreset.Box, 0), breatheTint(BreathePreset.Box, 2))
        assertEquals(breatheTint(BreathePreset.Reset, 0), breatheTint(BreathePreset.Reset, 1))
        val tints = (0..3).map { breatheTint(BreathePreset.Color, it) }
        assertEquals(4, tints.distinct().size)                     // one tint per phase
        assertEquals(tints[0], breatheTint(BreathePreset.Color, 4)) // cycle wraps
    }

    // (V2-e part 2: the flowerFor pin retired with Games.kt — PatternGlow,
    // ZenRipples and the orphaned GratitudeGarden left; the games REGISTRY
    // versions are the one implementation per behavior.)

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

    // ── Wave-1 Home rebuild (2026-08-03): relative time, mood tints,
    //    time-aware plan step ─────────────────────────────────────────────

    @Test
    fun relativeTime_buckets_minutes_hours_yesterday_days() {
        val now = java.time.OffsetDateTime.parse("2026-08-03T20:00:00+05:30")
        fun at(iso: String) = relativeTime(iso, now)
        assertEquals(RelTime.JustNow, at("2026-08-03T19:59:30+05:30"))
        assertEquals(RelTime.Minutes(45), at("2026-08-03T19:15:00+05:30"))
        assertEquals(RelTime.Hours(3), at("2026-08-03T17:00:00+05:30"))
        assertEquals(RelTime.Yesterday, at("2026-08-02T19:00:00+05:30"))
        assertEquals(RelTime.Days(3), at("2026-07-31T12:00:00+05:30"))
        // Honest degradation: no stamp / garbage / future -> no label.
        assertNull(relativeTime(null, now))
        assertNull(relativeTime("not-a-date", now))
        assertNull(relativeTime("2026-08-03T21:00:00+05:30", now))
    }

    @Test
    fun nextPlanStep_prefers_the_step_named_for_this_part_of_day() {
        val titles = listOf("Morning Breathing Exercise", "Midday Mindfulness", "Evening Unwind")
        val none = listOf(false, false, false)
        // 7 PM suggests the evening step, not the morning one listed first.
        assertEquals(2, nextPlanStepIndex(titles, none, hour = 19))
        assertEquals(0, nextPlanStepIndex(titles, none, hour = 8))
        assertEquals(1, nextPlanStepIndex(titles, none, hour = 14))
        // A done time-matched step falls through to the first undone.
        assertEquals(0, nextPlanStepIndex(titles, listOf(false, false, true), hour = 19))
        // Titles without time words keep the old first-undone behavior.
        assertEquals(1, nextPlanStepIndex(listOf("Stretch", "Journal"), listOf(true, false), hour = 19))
        // Everything done -> nothing to suggest.
        assertNull(nextPlanStepIndex(titles, listOf(true, true, true), hour = 19))
    }

    // ── Home polish waves (2026-08-03): header, banners, display mapping ──

    @Test
    fun eyebrowTemplate_rotates_daily_through_three_framings() {
        val a = eyebrowTemplateRes(30)
        val b = eyebrowTemplateRes(31)
        val c = eyebrowTemplateRes(32)
        // Three consecutive days wear three different framings, then repeat.
        assertEquals(3, setOf(a, b, c).size)
        assertEquals(a, eyebrowTemplateRes(33))
    }

    @Test
    fun earlierLine_holds_through_the_small_hours_then_lets_go() {
        val now = java.time.OffsetDateTime.parse("2026-08-03T01:00:00+05:30")
        val lastNight = relativeTime("2026-08-02T23:50:00+05:30", now)   // Hours bucket
        assertTrue(showEarlierLine(lastNight, hour = 1))
        // A Yesterday-bucket stamp still shows before 4am, not after.
        assertTrue(showEarlierLine(RelTime.Yesterday, hour = 3))
        assertEquals(false, showEarlierLine(RelTime.Yesterday, hour = 4))
        assertEquals(false, showEarlierLine(RelTime.Days(2), hour = 1))
        assertEquals(false, showEarlierLine(null, hour = 9))
    }

    @Test
    fun checkInLine_hides_the_legacy_onboarding_provenance_note() {
        val legacy = JSONObject().put("mood", "Anxious").put("note", "From onboarding")
        assertEquals("Anxious", checkInLine(legacy))
        val real = JSONObject().put("mood", "Good").put("note", "Clear")
        assertEquals("Good · Clear", checkInLine(real))
        val noteless = JSONObject().put("mood", "Low")
        assertEquals("Low", checkInLine(noteless))   // no dangling separator
    }

    @Test
    fun wireMoodNames_map_to_display_resources_and_unknowns_stay_raw() {
        assertEquals(R.string.mood_good, moodLabelResFor("Good"))
        assertEquals(R.string.mood_anxious, moodLabelResFor("anxious"))   // case-insensitive
        assertNull(moodLabelResFor("Ecstatic"))
        // The note maps only when it is the taxonomy's own preset for that mood.
        assertEquals(R.string.mood_good_note, moodNoteResFor("Good", "Clear"))
        assertNull(moodNoteResFor("Good", "had a nice walk"))
        assertNull(moodNoteResFor("Ecstatic", "Clear"))
    }

    @Test
    fun milestone_shows_late_holds_for_its_day_then_retires() {
        // Day 8, never celebrated: day 7's moment still fires.
        assertEquals(7, milestoneToShow(streak = 8, pref = null, today = "2026-08-03"))
        // Same day, already recorded: keeps showing.
        assertEquals(7, milestoneToShow(8, "7|2026-08-03", "2026-08-03"))
        // Next day: retired.
        assertNull(milestoneToShow(8, "7|2026-08-03", "2026-08-04"))
        // A new milestone reopens the line.
        assertEquals(14, milestoneToShow(14, "7|2026-08-03", "2026-08-10"))
        // Below the first milestone: nothing, ever.
        assertNull(milestoneToShow(2, null, "2026-08-03"))
        // Garbage pref degrades to "never celebrated".
        assertEquals(3, milestoneToShow(3, "not-a-pref", "2026-08-03"))
    }

    @Test
    fun homeSnapshot_round_trips_the_first_frame() {
        val week = listOf("S" to false, "M" to true)
        val recent = listOf(RecentCheckIn("Good · Clear", "Good", "2026-08-03T09:00:00Z", "Clear"))
        val snap = homeSnapshotOf("Smoke", "Reduce stress", 4, 2, week, recent)
        assertEquals(week, homeSnapshotWeek(snap))
        assertEquals(recent, homeSnapshotRecent(snap))
        assertEquals("Smoke", snap.optString("name"))
        assertEquals(4, snap.optInt("streak"))
        // A missing/foreign snapshot degrades to empty, never crashes.
        assertEquals(emptyList<Pair<String, Boolean>>(), homeSnapshotWeek(JSONObject()))
        assertEquals(emptyList<RecentCheckIn>(), homeSnapshotRecent(JSONObject()))
    }

    @Test
    fun checkInsToday_counts_only_local_today() {
        val moods = JSONArray()
            .put(JSONObject().put("created_at", "2026-08-03T10:00:00+05:30"))
            .put(JSONObject().put("created_at", "2026-08-03T22:00:00+05:30"))
            .put(JSONObject().put("created_at", "2026-08-02T23:00:00+05:30"))
            .put(JSONObject().put("created_at", "garbage"))
        val today = java.time.LocalDate.parse("2026-08-03")
        // The two 08-03 stamps count only if this JVM's zone agrees; assert on
        // the pure boundary instead: yesterday + garbage never count.
        assertTrue(checkInsToday(moods, today) <= 2)
        assertEquals(0, checkInsToday(JSONArray(), today))
    }

    @Test
    fun railArt_lets_water_titles_wear_the_wave_motif() {
        assertEquals("soundscape", artKindForTitle("Rain over quiet hills", "sleep"))
        assertEquals("soundscape", artKindForTitle("Ocean at dusk", "meditation"))
        assertEquals("sleep", artKindForTitle("Moonlit meadow", "sleep"))
        assertEquals("meditation", artKindForTitle("Body scan", "meditation"))
    }

    @Test
    fun planTail_stops_saying_zero_after_five_pm() {
        assertEquals(false, planTailUsesLeftForm(done = 0, hour = 16))
        assertTrue(planTailUsesLeftForm(done = 0, hour = 17))
        assertTrue(planTailUsesLeftForm(done = 0, hour = 23))
        // Any progress at all keeps the honest count.
        assertEquals(false, planTailUsesLeftForm(done = 1, hour = 21))
    }

    @Test
    fun planArt_follows_the_focus_not_a_fixed_purple() {
        assertEquals("sleep", planArtKind("Sleep before midnight"))
        assertEquals("meditation", planArtKind("Reduce stress"))
        assertEquals("meditation", planArtKind("Ease anxiety"))
        assertEquals("program", planArtKind("Drink more water"))
        assertEquals("program", planArtKind(""))
    }

    @Test
    fun moodTint_knows_every_wire_mood_and_declines_unknowns() {
        listOf("Good", "Anxious", "Low", "Tired").forEach {
            assertTrue("tint for $it", moodTintFor(it) != null)
        }
        assertNull(moodTintFor("Ecstatic"))   // future taxonomy value: untinted, not a crash
    }

    @Test
    fun onboardingMoodNote_uses_taxonomy_notes_never_provenance_jargon() {
        assertEquals("Loud thoughts", onboardingMoodNote("Anxious"))
        assertEquals("Need rest", onboardingMoodNote("Tired"))
        assertEquals("Heavy", onboardingMoodNote("Low"))
        assertEquals("", onboardingMoodNote("SomethingNew"))
    }

    @Test
    fun daySeparator_labels_day_changes_and_lets_local_bubbles_inherit() {
        val today = java.time.LocalDate.parse("2026-08-03")
        fun msg(role: String, iso: String) = Msg(role, "x", createdAt = iso)
        val list = listOf(
            msg("user", "2026-08-01T10:00:00+05:30"),
            msg("assistant", "2026-08-01T10:00:05+05:30"),
            msg("user", "2026-08-02T09:00:00+05:30"),
            msg("user", "2026-08-03T08:00:00+05:30"),
            Msg("user", "local, just sent"),   // no stamp: inherits, never labels
        )
        assertEquals("Aug 1", daySeparator(list, 0, today))
        assertNull(daySeparator(list, 1, today))
        assertEquals("YESTERDAY", daySeparator(list, 2, today))
        assertEquals("TODAY", daySeparator(list, 3, today))
        assertNull(daySeparator(list, 4, today))
    }

    @Test
    fun entriesThisMonth_counts_only_this_calendar_month_and_skips_garbage() {
        val today = java.time.LocalDate.parse("2026-08-03")
        val entries = listOf(
            Entry("a", "x", "2026-08-01", "none"),
            Entry("b", "x", "2026-08-03", "none"),
            Entry("c", "x", "2026-07-31", "none"),   // last month
            Entry("d", "x", "2025-08-10", "none"),   // last year, same month name
            Entry("e", "x", "not-a-date", "none"),
        )
        assertEquals(2, entriesThisMonth(entries, today))
        assertEquals(0, entriesThisMonth(emptyList(), today))
    }

    @Test
    fun toolkitRecentLabel_knows_every_practice_and_declines_the_rest() {
        listOf(
            "ground", "games", "bubblepop", "breathe/box",
            "breathe/reset", "cbt", "tipp", "imagery", "ritual", "gratitude",
            "sounds",
        ).forEach { assertTrue("label for $it", toolkitRecentLabelRes(it) != null) }
        // Crisis must never render as "pick up where you left off", and a
        // stale pref from a retired route renders nothing rather than crashing.
        // V2-e part 2: zenripples + patternglow are exactly that retired case —
        // a phone with either in `toolkit_recent` hides the chip, no crash.
        assertNull(toolkitRecentLabelRes("crisis"))
        assertNull(toolkitRecentLabelRes("retired_tool"))
        assertNull(toolkitRecentLabelRes("zenripples"))
        assertNull(toolkitRecentLabelRes("patternglow"))
    }

    @Test
    fun `every plan-step symbol opens a surface that can run it`() {
        // BUG-03: the daily plan's "Begin next unfinished step" used to call
        // togglePlanStep(done = true) — it said Begin and did Finish, ticking
        // off work nobody had done. It now OPENS the step through
        // planStepRoute, so that mapping is load-bearing and pinned here.
        //
        // Same symbol vocabulary as the Oracle widgets and the web Home
        // mapping (a hand-duplicated cross-stack contract).
        assertEquals("toolkit", planStepRoute("wind.snow"))
        assertEquals("toolkit", planStepRoute("wind"))
        assertEquals("sounds", planStepRoute("moon"))
        assertEquals("sounds", planStepRoute("bell"))
        assertEquals("journal", planStepRoute("book"))
        assertEquals("journal", planStepRoute("brain"))
        assertEquals("talk", planStepRoute("mic"))
        assertEquals("talk", planStepRoute("person.2"))
        assertEquals("talk", planStepRoute("heart"))

        // An unknown symbol resolves to nothing here; the caller falls back to
        // the toolkit rather than opening a route that does not exist — the
        // crash BUG-01 was about.
        assertNull(planStepRoute("sparkles"))
        assertNull(planStepRoute(""))
    }

    // ── The care card's personalization claim ───────────────────────
    // "picked with you, not for you" is true of a plan the SERVER built from
    // this account's signals, and of nothing else (CLAIMS_MAP §3). The line
    // used to sit outside the branch, so the identical-for-everyone fallback
    // printed it too.
    @Test
    fun only_a_real_plan_claims_it_was_picked_with_you() {
        assertTrue(showsCarePlanProvenance(HeroKind.PLAN_STEP))
        assertTrue(showsCarePlanProvenance(HeroKind.PLAN_DONE))
        assertFalse(showsCarePlanProvenance(HeroKind.FALLBACK))
        assertFalse(showsCarePlanProvenance(HeroKind.LOADING))
    }

    @Test
    fun an_offline_session_falls_back_instead_of_shimmering_for_a_plan_that_cannot_arrive() {
        // planLoaded is passed as `planLoaded || servedStale`: offline, the
        // request will never answer, and LOADING would leave a blank shimmer
        // in the most important card on Home.
        assertEquals(HeroKind.LOADING, heroKindFor(planLoaded = false, hasPlan = false, hasNextStep = false))
        assertEquals(HeroKind.FALLBACK, heroKindFor(planLoaded = true, hasPlan = false, hasNextStep = false))
        assertEquals(HeroKind.PLAN_STEP, heroKindFor(planLoaded = true, hasPlan = true, hasNextStep = true))
        assertEquals(HeroKind.PLAN_DONE, heroKindFor(planLoaded = true, hasPlan = true, hasNextStep = false))
    }
}
