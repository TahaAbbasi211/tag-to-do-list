package com.tag.routine.ui.motion

/**
 * Pure motion timing and interpolation helpers for Insights charts and heatmap waves.
 */
object InsightsMotionLogic {

    const val HEATMAP_CELL_WAVE_SPAN = 0.28f

    /** Computes staggered entrance delay for a bar column while keeping animations snappy. */
    fun barStaggerDelayMs(barIndex: Int, level: AnimationLevel): Int {
        val idx = barIndex.coerceAtLeast(0)
        return when (level) {
            AnimationLevel.FULL -> (idx * 50).coerceAtMost(350)
            AnimationLevel.REDUCED -> (idx * 20).coerceAtMost(120)
            AnimationLevel.OFF -> 0
        }
    }

    /**
     * Evaluates normalized visibility/scale `[0f..1f]` for a heatmap cell at `(weekIndex, dayIndex)`
     * during a diagonal wave sweep `waveProgress in [0f..1f]`. Every cell reaches `1f` when
     * `waveProgress >= 1f`, and all cells return `1f` immediately under [AnimationLevel.OFF].
     */
    fun diagonalCellProgress(
        weekIndex: Int,
        dayIndex: Int,
        totalWeeks: Int,
        waveProgress: Float,
        level: AnimationLevel,
    ): Float {
        if (level.isOff || waveProgress >= 1f) return 1f
        if (waveProgress <= 0f) return 0f

        val maxDiag = (totalWeeks - 1).coerceAtLeast(0) + 6
        val diag = (weekIndex.coerceAtLeast(0) + dayIndex.coerceIn(0, 6)).coerceAtMost(maxDiag)
        val span = HEATMAP_CELL_WAVE_SPAN
        val start = if (maxDiag == 0) 0f else (diag.toFloat() / maxDiag.toFloat()) * (1f - span)
        return ((waveProgress - start) / span).coerceIn(0f, 1f)
    }

    /** Clamps raw completion ratios to `[0f..1f]` for trend sparkline plotting. */
    fun sparklineNormalizedPoints(ratios: List<Float>): List<Float> =
        ratios.map { it.coerceIn(0f, 1f) }
}
