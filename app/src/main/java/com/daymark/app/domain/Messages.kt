package com.daymark.app.domain

import java.time.LocalDate

object Messages {
    val motivational = listOf(
        "One day at a time is enough.",
        "Your progress is real, even on hard days.",
        "A slip doesn't erase what you've built.",
        "Be as kind to yourself as you'd be to a friend.",
        "Cravings pass. You can ride this one out.",
        "Every day you choose this counts.",
        "You can begin again at any moment.",
        "Rest, water, a slow breath. Small things help.",
        "You've handled hard days before.",
        "Progress is not a straight line.",
        "Asking for support is a strength.",
        "Today you're doing something good for future you.",
        "Notice how far you've come.",
        "You don't have to be perfect. Just keep going.",
        "This moment is temporary. You are not alone.",
        "An urge is a wave. You don't have to ride it to the shore.",
        "Shame keeps habits alive. Kindness helps them fade.",
        "Change the setting, change the urge: step outside, move, call someone.",
        "Every urge you ride out rewires your brain a little.",
        "What you're building is worth more than a moment's relief.",
    )

    /** Stable for a whole day so the widget and dashboard agree. */
    fun forDate(date: LocalDate): String =
        motivational[(date.toEpochDay() % motivational.size).toInt().let { if (it < 0) it + motivational.size else it }]

    val afterRelapse = listOf(
        "Thank you for being honest with yourself. That takes courage.",
        "This is a moment, not your whole story.",
        "Everything you've built still counts. Let's keep going.",
        "Be gentle with yourself today. Tomorrow is a fresh page.",
    )

    fun greeting(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello, night owl"
    }

    // ---- Build-a-habit copy -------------------------------------------------------------------

    val buildQuotes = listOf(
        "Small steps, repeated, become who you are.",
        "A missed day doesn't undo your progress.",
        "Consistency beats intensity.",
        "You only have to do today's part.",
        "Showing up counts, even in a small way.",
        "Every check-in is a vote for the person you're becoming.",
        "Start small. Keep it easy. Let it grow.",
        "Rest days are part of the plan.",
        "Progress, not perfection.",
        "Be proud of the days you showed up.",
    )

    fun buildQuote(date: LocalDate): String =
        buildQuotes[(date.toEpochDay() % buildQuotes.size).toInt().let { if (it < 0) it + buildQuotes.size else it }]

    /** The state line under the build hero. [remaining] is already formatted with its unit, e.g. "750 ml". */
    fun buildState(done: Boolean, hasGoal: Boolean, started: Boolean, restDay: Boolean, remaining: String, seed: Int): String = when {
        done && restDay -> "Bonus day. Lovely."
        done -> pick(seed, "Goal reached. Well done 🎉", "All done for today 🎉", "Done. That's a win 🎉")
        restDay -> pick(seed, "Rest day. Enjoy it.", "Nothing planned today. Rest easy.")
        !hasGoal -> pick(seed, "Ready when you are.", "One tap and today is done.")
        !started -> pick(seed, "A fresh day. $remaining to go.", "Whenever you're ready: $remaining.")
        else -> pick(seed, "Nice. $remaining to go.", "Going well. $remaining left.", "Keep it up. $remaining to go.")
    }

    fun buildStreak(days: Int, longest: Int): String = when {
        days <= 0 && longest > 0 -> "A fresh start. Your best of $longest ${if (longest == 1) "day" else "days"} is still yours."
        days <= 0 -> "Your first day is whenever you begin."
        days == 1 -> "Day one is the hardest. Nicely done."
        days < 7 -> "Building momentum."
        days < 30 -> "A real rhythm is forming."
        else -> "This is part of who you are now."
    }

    const val BUILD_MISS_NOTE = "A missed day doesn't undo your progress."

    private fun pick(seed: Int, vararg options: String): String = options[((seed % options.size) + options.size) % options.size]
}
