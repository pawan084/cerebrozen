package com.cerebrozen.app.ui.screens

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The safety plan's parse layer, and the contract the sections have to keep.
 *
 * The three-way state this screen resolves — loaded live / served from cache /
 * failed with nothing cached — was verified on hardware rather than here,
 * because two of the three need the network taken away. What is pinned here is
 * the part a refactor can quietly break: the section list is a cross-stack
 * contract, and a missing field must read as empty, never as absent.
 */
class SafetyPlanTest {

    @Test
    fun theSixStanleyBrownSectionsAreTheContract() {
        // Order is the order the user meets them, and the keys are wire values
        // shared with iOS, the browser client and backend/models/safety_plan.py.
        assertEquals(
            listOf(
                "warning_signs",
                "internal_coping",
                "social_distractors",
                "social_support",
                "professionals",
                "means_safety",
                "notes",
            ),
            SAFETY_PLAN_FIELDS,
        )
    }

    @Test
    fun everySectionParsesEvenWhenTheServerOmitsIt() {
        // An older server, or a plan written before a section existed, must not
        // make that box disappear — a safety plan with a missing section is a
        // plan the user cannot finish.
        val partial = JSONObject("""{"warning_signs":"Skipping meals","version":2}""")
        val values = parseSafetyPlan(partial)
        assertEquals(SAFETY_PLAN_FIELDS.size, values.size)
        assertEquals("Skipping meals", values["warning_signs"])
        assertEquals("", values["means_safety"])
        assertTrue(values.keys.containsAll(SAFETY_PLAN_FIELDS))
    }

    @Test
    fun noPlanYetIsAnEmptyMapNotACrash() {
        assertEquals(emptyMap<String, String>(), parseSafetyPlan(null))
    }
}
