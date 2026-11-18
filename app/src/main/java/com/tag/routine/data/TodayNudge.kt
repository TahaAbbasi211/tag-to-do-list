package com.tag.routine.data

/**
 * The one-line nudge shown on the Today hero card.
 *
 * Kept as a plain enum rather than a ready-made sentence so the decision is pure logic that
 * can be unit tested, while the wording stays in the five `strings.xml` files.
 */
enum class TodayNudge {

    /** Nothing is scheduled for the day. */
    EMPTY,

    /** Everything scheduled is ticked. */
    ALL_DONE,

    /** Exactly one item is still open. */
    ONE_LEFT,

    /** Late evening: the useful suggestion is to look at tomorrow. */
    NIGHT,

    /** Morning with nothing done yet. */
    MORNING,

    /** At least half of the day is done, still in progress. */
    HALFWAY,

    /** Anything else. */
    KEEP_GOING;

    companion object {

        /** Evening from this hour counts as night. */
        const val NIGHT_START_HOUR = 21

        /** Before this hour the day has not really started. */
        const val DAY_START_HOUR = 5

        /** Morning ends at this hour (inclusive). */
        const val MORNING_END_HOUR = 11

        /**
         * Picks the nudge for a day with [total] scheduled items of which [completed] are
         * ticked, at [hour] on the 24-hour clock.
         *
         * The most concrete information wins: "nothing scheduled", "all done" and "one left"
         * are always shown, whatever the time. The time of day only decides between the
         * remaining messages, so a late-evening user with one open item still sees that.
         */
        fun forDay(hour: Int, total: Int, completed: Int): TodayNudge {
            if (total <= 0) return EMPTY
            val done = completed.coerceIn(0, total)
            if (done >= total) return ALL_DONE
            if (total - done == 1) return ONE_LEFT

            val normalizedHour = ((hour % 24) + 24) % 24
            if (normalizedHour >= NIGHT_START_HOUR || normalizedHour < DAY_START_HOUR) {
                return NIGHT
            }
            val progress = done.toFloat() / total
            if (progress >= 0.5f) return HALFWAY
            if (normalizedHour in DAY_START_HOUR..MORNING_END_HOUR) return MORNING
            return KEEP_GOING
        }
    }
}
