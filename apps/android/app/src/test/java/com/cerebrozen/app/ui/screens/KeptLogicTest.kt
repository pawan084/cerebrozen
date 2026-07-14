package com.cerebrozen.app.ui.screens

/* The keep-half of the reference's ScreenLogicTest, recovered after the B2C
 * strip: the DPDP consent notice (language routing + every language carrying
 * all six categories) and the Reduce Motion scale rule. The stripped half
 * tested deleted sleep/talk/journal parsers. */

import org.junit.Assert.assertEquals
import org.junit.Test

class KeptLogicTest {

    @Test
    fun reduceMotionFromScale_only_true_when_animations_are_off() {
        assertEquals(true, reduceMotionFromScale(0f))    // "Remove animations" on
        assertEquals(false, reduceMotionFromScale(1f))   // normal
        assertEquals(false, reduceMotionFromScale(0.5f)) // slowed, not removed
        assertEquals(false, reduceMotionFromScale(2f))   // sped up
    }

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
}
