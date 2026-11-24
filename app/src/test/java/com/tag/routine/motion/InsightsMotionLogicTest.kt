package com.tag.routine.motion

import com.tag.routine.ui.motion.AnimationLevel
import com.tag.routine.ui.motion.InsightsMotionLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsMotionLogicTest {

    @Test
    fun `barStaggerDelayMs respects animation level and upper bounds`() {
        assertEquals(0, InsightsMotionLogic.barStaggerDelayMs(-3, AnimationLevel.FULL))
        assertEquals(0, InsightsMotionLogic.barStaggerDelayMs(0, AnimationLevel.FULL))
        assertEquals(150, InsightsMotionLogic.barStaggerDelayMs(3, AnimationLevel.FULL))
        assertEquals(350, InsightsMotionLogic.barStaggerDelayMs(20, AnimationLevel.FULL))

        assertEquals(60, InsightsMotionLogic.barStaggerDelayMs(3, AnimationLevel.REDUCED))
        assertEquals(120, InsightsMotionLogic.barStaggerDelayMs(20, AnimationLevel.REDUCED))

        assertEquals(0, InsightsMotionLogic.barStaggerDelayMs(5, AnimationLevel.OFF))
    }

    @Test
    fun `diagonalCellProgress completes under OFF and wave end and preserves diagonal order`() {
        assertEquals(
            1f,
            InsightsMotionLogic.diagonalCellProgress(
                weekIndex = 5,
                dayIndex = 4,
                totalWeeks = 12,
                waveProgress = 0.2f,
                level = AnimationLevel.OFF,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            InsightsMotionLogic.diagonalCellProgress(
                weekIndex = 0,
                dayIndex = 0,
                totalWeeks = 12,
                waveProgress = 0f,
                level = AnimationLevel.FULL,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            InsightsMotionLogic.diagonalCellProgress(
                weekIndex = 11,
                dayIndex = 6,
                totalWeeks = 12,
                waveProgress = 1f,
                level = AnimationLevel.FULL,
            ),
            0.0001f,
        )

        val head = InsightsMotionLogic.diagonalCellProgress(
            weekIndex = 0,
            dayIndex = 0,
            totalWeeks = 12,
            waveProgress = 0.5f,
            level = AnimationLevel.FULL,
        )
        val tail = InsightsMotionLogic.diagonalCellProgress(
            weekIndex = 11,
            dayIndex = 6,
            totalWeeks = 12,
            waveProgress = 0.5f,
            level = AnimationLevel.FULL,
        )
        assertTrue(head in 0f..1f)
        assertTrue(tail in 0f..1f)
        assertTrue(head >= tail)

        // Single-week safety guard
        assertTrue(
            InsightsMotionLogic.diagonalCellProgress(
                weekIndex = 0,
                dayIndex = 2,
                totalWeeks = 1,
                waveProgress = 0.5f,
                level = AnimationLevel.FULL,
            ) in 0f..1f,
        )
    }

    @Test
    fun `sparklineNormalizedPoints clamps ratios into unit interval`() {
        assertEquals(emptyList<Float>(), InsightsMotionLogic.sparklineNormalizedPoints(emptyList()))
        assertEquals(
            listOf(0f, 0f, 0.45f, 1f, 1f),
            InsightsMotionLogic.sparklineNormalizedPoints(listOf(-0.2f, 0f, 0.45f, 1f, 1.3f)),
        )
    }
}
