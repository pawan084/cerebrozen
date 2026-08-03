package com.cerebrozen.app.ui.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineToolContentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun guidedImageryHasFourCompleteFiveStepJourneys() {
        assertEquals(4, OfflineToolContent.journeys.size)
        assertEquals(4, OfflineToolContent.journeys.map { it.id }.toSet().size)
        OfflineToolContent.journeys.forEach { journey ->
            assertEquals(5, journey.steps.size)
            assertResource(journey.titleRes)
            journey.steps.forEach(::assertResource)
            assertResource(journey.endingRes)
        }
    }

    @Test
    fun bodyScanAndGroundingSequencesAreCompleteAndOrdered() {
        assertEquals(8, OfflineToolContent.bodyScan.size)
        assertTrue(OfflineToolContent.bodyScan.all { it.seconds > 0 })
        OfflineToolContent.bodyScan.forEach { assertResource(it.partRes); assertResource(it.instructionRes) }

        assertEquals(listOf(5, 4, 3, 2, 1), OfflineToolContent.grounding.map { it.count })
        OfflineToolContent.grounding.forEach { assertResource(it.senseRes); assertResource(it.promptRes) }
    }

    @Test
    fun insightReelAndEducationalProgramsShipLocalContent() {
        assertEquals(6, OfflineToolContent.insights.size)
        OfflineToolContent.insights.forEach { assertResource(it.kickerRes); assertResource(it.titleRes); assertResource(it.bodyRes) }

        assertEquals(6, OfflineToolContent.cbtI.modules.size)
        assertEquals(8, OfflineToolContent.mbct.modules.size)
        listOf(OfflineToolContent.cbtI, OfflineToolContent.mbct).forEach { program ->
            assertResource(program.titleRes)
            assertResource(program.subtitleRes)
            program.modules.forEach { assertResource(it.titleRes); assertResource(it.bodyRes); assertResource(it.practiceRes) }
        }
    }

    private fun assertResource(id: Int) {
        assertTrue("Resource $id must contain user-facing copy", context.getString(id).isNotBlank())
    }
}
