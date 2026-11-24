package com.tag.routine.ui.motion

/**
 * Pure detector for celebrated streak milestones (3, 7, 14, 30, 50, 100 days).
 */
object StreakMilestones {

    val MILESTONES: Set<Int> = setOf(3, 7, 14, 30, 50, 100)

    fun isMilestone(streak: Int): Boolean = streak in MILESTONES

    fun milestoneFor(streak: Int): Int? = streak.takeIf { it in MILESTONES }

    /**
     * Returns the milestone reached when moving from [previous] to [current], or `null`
     * if [current] is not a milestone or did not strictly advance past [previous].
     */
    fun reachedNewMilestone(previous: Int, current: Int): Int? =
        if (current > previous && current in MILESTONES) current else null
}
