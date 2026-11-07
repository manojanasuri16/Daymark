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
}
