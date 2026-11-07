package com.daymark.app.domain

data class Mood(val value: Int, val emoji: String, val label: String)

object Options {
    val triggers = listOf(
        "Stress", "Boredom", "Loneliness", "Anxiety", "Anger", "Sadness",
        "Tiredness", "Social pressure", "Celebration", "Cravings", "Screens",
        "Late night", "Conflict", "Habit cue", "Arousal", "Payday / cash", "Rejection", "After drinking",
        "Browsing apps", "Hotel / alone away",
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
        "Alcohol", "Smoking", "Vaping", "Porn", "Masturbation", "Paid sex", "Social media", "Gambling",
        "Junk food", "Doomscrolling", "Caffeine",
    )

    fun mood(value: Int): Mood? = moods.firstOrNull { it.value == value }
}
