package com.daymark.app.domain

data class Mood(val value: Int, val emoji: String, val label: String)

/** A ready-made "build a habit" starting point. [target] 0 = simple check-off. Times are minutes after midnight. */
data class BuildPreset(
    val name: String,
    val emoji: String,
    val target: Double = 0.0,
    val unit: String = "",
    val step: Double = 1.0,
    val days: Int = Schedule.EVERY_DAY,
    val reminder: ReminderConfig = ReminderConfig(),
)

object Options {
    val triggers = listOf(
        "Stress", "Boredom", "Loneliness", "Anxiety", "Anger", "Sadness",
        "Tiredness", "Social pressure", "Celebration", "Cravings", "Screens",
        "Late night", "Conflict", "Habit cue", "Payday / cash", "Rejection", "After drinking",
        "Browsing apps",
    )

    val situations = listOf(
        "Alone at home", "In bed", "With friends", "At work", "Out & about", "Online", "Party / event", "Travelling", "Other",
    )

    val moods = listOf(
        Mood(1, "😞", "Very low"),
        Mood(2, "😕", "Low"),
        Mood(3, "😐", "Okay"),
        Mood(4, "🙂", "Good"),
        Mood(5, "😄", "Great"),
    )

    /** Neutral, hopeful symbols — deliberately no substance imagery that could act as a cue. */
    val emojis = listOf("🌱", "☀️", "🌊", "🕊️", "💪", "🧘", "🌙", "🔥", "⭐", "🏔️", "🦋", "💧", "🍃", "❤️", "🚭", "📵")

    val habitSuggestions = listOf(
        "Alcohol", "Smoking", "Vaping", "Doomscrolling", "Social media", "Gambling",
        "Junk food", "Caffeine", "Sugar", "Online shopping", "Gaming", "Nail biting",
    )

    val buildEmojis = listOf(
        "💧", "📖", "🏋️", "🧘", "🚶", "🌙", "✍️", "🤸", "🥗", "🎨", "🎵", "🧹", "☀️", "🌱", "💪", "⭐",
    )

    val buildPresets = listOf(
        BuildPreset("Drink water", "💧", 3.0, "L", 0.25, reminder = ReminderConfig(ReminderMode.INTERVAL, 60, 8 * 60, 21 * 60)),
        BuildPreset("Read", "📖", 20.0, "min", 5.0, reminder = ReminderConfig(ReminderMode.TIMES, times = listOf(21 * 60))),
        BuildPreset(
            "Workout", "🏋️",
            days = Schedule.of(listOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY)),
            reminder = ReminderConfig(ReminderMode.TIMES, times = listOf(18 * 60)),
        ),
        BuildPreset("Meditate", "🧘", 10.0, "min", 5.0, reminder = ReminderConfig(ReminderMode.TIMES, times = listOf(7 * 60 + 30))),
        BuildPreset("Walk", "🚶", 8000.0, "steps", 1000.0, reminder = ReminderConfig(ReminderMode.TIMES, times = listOf(17 * 60 + 30))),
        BuildPreset("Sleep early", "🌙", reminder = ReminderConfig(ReminderMode.TIMES, times = listOf(22 * 60))),
        BuildPreset("Journal", "✍️", reminder = ReminderConfig(ReminderMode.TIMES, times = listOf(21 * 60 + 30))),
        BuildPreset("Stretch", "🤸", reminder = ReminderConfig(ReminderMode.TIMES, times = listOf(8 * 60))),
    )

    fun mood(value: Int): Mood? = moods.firstOrNull { it.value == value }
}
