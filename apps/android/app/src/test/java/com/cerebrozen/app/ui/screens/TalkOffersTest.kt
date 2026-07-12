package com.cerebrozen.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Try together" visibility (TalkScreen, REDESIGN §3.3): the offers appear only
 * after the companion's latest reply in a real exchange — never while it's
 * thinking or streaming, never after the user's own message.
 */
class TalkOffersTest {

    @Test
    fun shows_after_an_assistant_reply_in_a_real_exchange() {
        assertEquals(true, showTryTogether(2, "assistant", busy = false, streaming = false))
        assertEquals(true, showTryTogether(8, "assistant", busy = false, streaming = false))
    }

    @Test
    fun hidden_before_a_real_exchange_exists() {
        assertEquals(false, showTryTogether(0, null, busy = false, streaming = false))
        assertEquals(false, showTryTogether(1, "assistant", busy = false, streaming = false))
    }

    @Test
    fun hidden_while_the_companion_is_composing() {
        assertEquals(false, showTryTogether(4, "assistant", busy = true, streaming = false))
        assertEquals(false, showTryTogether(4, "assistant", busy = false, streaming = true))
    }

    @Test
    fun hidden_when_the_user_spoke_last() {
        assertEquals(false, showTryTogether(3, "user", busy = false, streaming = false))
    }
}
