package com.daymark.app.domain

data class Milestone(val days: Int, val title: String, val subtitle: String)

object Milestones {
    val all = listOf(
        Milestone(1, "First Light", "24 hours. The hardest step is behind you."),
        Milestone(3, "Three Sunrises", "Your body is already thanking you."),
        Milestone(7, "One Week", "Seven days of choosing yourself."),
        Milestone(14, "Fortnight", "Two weeks of steady ground."),
        Milestone(30, "One Month", "A full month. New habits are forming."),
        Milestone(60, "Two Months", "Momentum is on your side."),
        Milestone(90, "Ninety Days", "A true turning point."),
        Milestone(180, "Half a Year", "Six months of growth."),
        Milestone(270, "Nine Months", "Look how far you've come."),
        Milestone(365, "One Year", "A whole year. Remarkable."),
        Milestone(730, "Two Years", "This is who you are now."),
        Milestone(1825, "Five Years", "A lasting transformation."),
    )

    /** Achievements are earned against the longest streak ever, so they are never lost. */
    fun earned(longestDays: Long): List<Milestone> = all.filter { it.days <= longestDays }

    fun next(currentDays: Long): Milestone? = all.firstOrNull { it.days > currentDays }

    fun previous(currentDays: Long): Milestone? = all.lastOrNull { it.days <= currentDays }

    /** 0..1 progress from the previous milestone to the next one, using exact milliseconds. */
    fun progress(currentMillis: Long): Float {
        val days = currentMillis / StreakStats.DAY_MS
        val next = next(days) ?: return 1f
        val prevDays = previous(days)?.days ?: 0
        val span = (next.days - prevDays) * StreakStats.DAY_MS
        val into = currentMillis - prevDays * StreakStats.DAY_MS
        return (into.toFloat() / span).coerceIn(0f, 1f)
    }
}
