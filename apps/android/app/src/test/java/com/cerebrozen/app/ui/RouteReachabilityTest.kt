package com.cerebrozen.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every route registered in the graph must have something that navigates to it.
 *
 * `guidedimagery` sat in the NavHost with **zero** references anywhere else in
 * the app — four finished journeys, five steps each with voice cues, that no
 * user could open. It survived an entire redesign that way, because nothing
 * connects "this route is registered" to "this route can be reached": the
 * compiler is happy, lint is happy, and the screen renders perfectly the moment
 * you deep-link to it in a debug build.
 *
 * Registering a screen is a promise that it is part of the product. This is the
 * check that the promise is kept.
 */
class RouteReachabilityTest {

    private val uiRoot = File("src/main/java/com/cerebrozen/app/ui")
    private val appFile = File(uiRoot, "CereBroApp.kt")

    /**
     * Routes with no in-app navigation, deliberately, each for a stated reason.
     *
     * Kept as an explicit list rather than a loosened rule so that adding one is
     * a decision someone writes down. `unreachable routes are all still
     * registered` keeps it honest: delete a route and its excuse must go too.
     */
    private val knownUnreachable = mapOf(
        // (V2-e: the talk/live, talk/chat and dailyplan aliases this list used
        // to excuse were deleted from the graph — the excuse left with them.)
        // Opened only when the model emits the matching activity widget
        // (TalkScreen's "intention_set" / "one_good_thing" mapping), so there is
        // no static caller by design.
        "intention" to "opened by an AI activity widget",
        "onegoodthing" to "opened by an AI activity widget",
        // V2-d: Explore's tab went back to Sleep (REDESIGN_V2 §2, owner-approved
        // 2026-08-15). The route stays registered for the `cerebro://explore`
        // deeplink (EXTERNAL_ROUTES) until the V2-e library merge decides its
        // final shape — deeplinks are not in-app navigation, so it lands here.
        "explore" to "deeplink-only since the V2-d tab swap",
    )

    /** Only real navigation counts. A route named in an accent `when` branch or
     * in the bottom-bar visibility set is *styled*, not reachable — that is
     * exactly the trap that made `talk/live` look connected. */
    private fun navigatesTo(route: String, corpus: String): Boolean =
        listOf(
            """onOpen("$route")""",
            """open("$route")""",
            """openTool("$route")""",
            """navigate("$route")""",
            """-> "$route"""",
        ).any { it in corpus }

    private fun registeredRoutes(): List<String> =
        Regex("""composable\("([^"]+)"""").findAll(appFile.readText())
            .map { it.groupValues[1] }
            .filterNot { "{" in it } // parameterised routes are opened with an argument
            .toList()

    private fun corpus(): String =
        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

    @Test
    fun `the scan finds the route table`() {
        // Without this, a rename of composable() would make every assertion here
        // pass over an empty list.
        assertTrue("found no registered routes — did the NavHost move?", registeredRoutes().size >= 40)
    }

    @Test
    fun `every registered route can be reached`() {
        val corpus = corpus()
        val orphans = registeredRoutes()
            .filterNot { it in knownUnreachable }
            .filterNot { navigatesTo(it, corpus) }
        assertTrue(
            "registered in the graph but nothing navigates there — either give it a door " +
                "or delete it, and if it is deliberate add it to knownUnreachable with a " +
                "reason: $orphans",
            orphans.isEmpty(),
        )
    }

    @Test
    fun `unreachable routes are all still registered`() {
        // Stops the excuse list outliving the routes it excuses.
        val registered = registeredRoutes().toSet()
        val stale = knownUnreachable.keys.filterNot { it in registered }
        assertTrue("knownUnreachable names routes that no longer exist: $stale", stale.isEmpty())
    }

    @Test
    fun `guidedimagery has a door`() {
        // The specific regression this suite was written for, named so a failure
        // reads as what it is rather than as one entry in a list.
        assertTrue(
            "guidedimagery is unreachable again — four finished journeys behind no entrance",
            navigatesTo("guidedimagery", corpus()),
        )
    }
}
