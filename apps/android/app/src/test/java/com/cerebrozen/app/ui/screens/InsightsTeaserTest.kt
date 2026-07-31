package com.cerebrozen.app.ui.screens

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The number under the Home "This week" teaser.
 *
 * A teaser that overstates is worse than one that says nothing, so the count is
 * pinned at both ends of its window and against rows it cannot read.
 */
class InsightsTeaserTest {

    private val today = LocalDate.of(2026, 7, 31)

    /** A mood row as the server sends it — UTC-stamped, mid-afternoon. */
    private fun moodsOn(vararg days: LocalDate): JSONArray {
        val arr = JSONArray()
        days.forEach { arr.put(JSONObject().put("created_at", "${it}T12:00:00+00:00")) }
        return arr
    }

    @Test
    fun `counts today and the six days behind it`() {
        // Seven days inclusive, so it lines up with the presence ring beside it.
        val week = (0..6).map { today.minusDays(it.toLong()) }.toTypedArray()
        assertEquals(7, checkInsThisWeek(moodsOn(*week), today))
    }

    @Test
    fun `the eighth day back is outside the window`() {
        assertEquals(0, checkInsThisWeek(moodsOn(today.minusDays(7)), today))
        assertEquals(1, checkInsThisWeek(moodsOn(today.minusDays(6)), today))
    }

    @Test
    fun `several check-ins on one day all count`() {
        // The teaser counts check-ins, not days — the presence ring counts days.
        assertEquals(3, checkInsThisWeek(moodsOn(today, today, today), today))
    }

    @Test
    fun `a future timestamp is not counted`() {
        // Clock skew on the device shouldn't inflate the week.
        assertEquals(0, checkInsThisWeek(moodsOn(today.plusDays(1)), today))
    }

    @Test
    fun `rows without a readable timestamp are skipped, not guessed`() {
        val arr = JSONArray()
            .put(JSONObject().put("created_at", "${today}T12:00:00+00:00"))
            .put(JSONObject().put("created_at", "nonsense"))
            .put(JSONObject())   // no field at all
        assertEquals(1, checkInsThisWeek(arr, today))
    }

    @Test
    fun `no check-ins reads as zero so the teaser falls back to its plain subtitle`() {
        assertEquals(0, checkInsThisWeek(JSONArray(), today))
    }
}
