package com.daymark.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Background, accent (ring / bars) and text colour of an event card (ARGB). */
data class ColorPreset(val name: String, val bg: Int, val accent: Int, val text: Int)

/** One of the eight "what would you like to count toward?" choices. [ideas] opens the template picker. */
data class EventStarter(val id: String, val emoji: String, val label: String, val kind: EventKind, val suggestedName: String, val ideas: Boolean = false)

/** An idea in the "Give me ideas" picker. */
data class EventTemplate(
    val emoji: String,
    val name: String,
    val kind: EventKind,
    val design: EventDesign = EventDesign.FONTY,
    /** Index into [EventDefaults.presets]. */
    val preset: Int = 0,
) {
    val title get() = "$emoji $name"

    /** Fresh options for this template's design and colours. */
    fun options(): EventOptions = EventDefaults.optionsFor(kind, design, preset)
}

object EventDefaults {
    private fun c(hex: Long) = (0xFF000000L or hex).toInt()

    /** About ten of our own colour looks. */
    val presets: List<ColorPreset> = listOf(
        ColorPreset("Sunrise", c(0xFFD966), c(0x3A2B2E), c(0x3A2B2E)),
        ColorPreset("Coral", c(0xE8604C), c(0x3A1F1B), c(0xFFFFFF)),
        ColorPreset("Citrus", c(0xF58A4B), c(0xFFE2CC), c(0xFFFFFF)),
        ColorPreset("Sky", c(0x1E8FD6), c(0xFFB938), c(0xFFFFFF)),
        ColorPreset("Slate", c(0x3C4254), c(0x5ED8D0), c(0xFFFFFF)),
        ColorPreset("Sage", c(0xB4CFC8), c(0x2F5D50), c(0x1F2D29)),
        ColorPreset("Blush", c(0xF7A08A), c(0x7A2E2A), c(0x2B1512)),
        ColorPreset("Mist", c(0xDADADF), c(0x3E8E55), c(0x24262B)),
        ColorPreset("Grape", c(0x6B4FBF), c(0xF5D76E), c(0xFFFFFF)),
        ColorPreset("Midnight", c(0x14171F), c(0x8FA6FF), c(0xF0F2FA)),
    )

    /** 40 swatches (8 columns of 5 tonal steps) for the colour sheet. */
    val palette: List<Int> = listOf(
        0x1B5E57, 0x2E8B80, 0x56B3A6, 0x9DD5CC, 0xD3EEE9,
        0x8E1B1B, 0xD64545, 0xF0786A, 0xF7ADA3, 0xFBD8D3,
        0xA86400, 0xE29A00, 0xF5C22E, 0xFAD97A, 0xFDF0C2,
        0x0B3F7A, 0x1E7BD6, 0x4FB0F0, 0x9AD6F7, 0xD6EEFC,
        0x3E2C23, 0x6E5242, 0x9C8272, 0xC8B5A8, 0xE8DED6,
        0x15181E, 0x4A505C, 0x8A909C, 0xD0D3DA, 0xFFFFFF,
        0x4A1F6B, 0x8442B8, 0xB874E0, 0xE0A8F0, 0xF4D6FA,
        0x1E6B2E, 0x3DBE5E, 0x7FDB95, 0xB9ECC6, 0xE2F7E8,
    ).map { c(it.toLong()) }

    fun preset(i: Int) = presets[i.mod(presets.size)]

    /** Default style for a kind: the first preset, a Swiss ring for ones that have an end, text for open-ended. */
    fun optionsFor(kind: EventKind, design: EventDesign? = null, presetIndex: Int? = null): EventOptions {
        val pick = presetIndex ?: when (kind) { EventKind.COUNTDOWN -> 4; EventKind.SINCE -> 0; EventKind.TIMER -> 0 }
        val p = preset(pick)
        val d = design ?: if (kind == EventKind.SINCE) EventDesign.FONTY else EventDesign.SWISS
        return EventOptions(design = d, bg = p.bg, accent = p.accent, text = p.text)
    }

    /** The four calendar-period events seeded on first open: Today, This week, This month, This year. */
    fun periodEvents(now: Long, zone: ZoneId, firstDay: DayOfWeek, createdAt: Long = now): List<CountEvent> {
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
        fun ms(d: java.time.LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDay))
        val monthStart = today.withDayOfMonth(1)
        val yearStart = today.withDayOfYear(1)
        fun make(name: String, repeat: EventRepeat, s: java.time.LocalDate, e: java.time.LocalDate, preset: Int, percent: Boolean, up: Boolean) =
            CountEvent(
                name = name, kind = EventKind.COUNTDOWN, startAt = ms(s), endAt = ms(e), repeat = repeat,
                countUp = up, createdAt = createdAt, modifiedAt = createdAt,
                options = optionsFor(EventKind.COUNTDOWN, EventDesign.SWISS, preset).copy(
                    showPercent = percent, multiUnits = if (percent) 0 else 3,
                    accessory = if (percent) Accessory.NONE else Accessory.AUTO,
                    roundUp = false,
                ),
            )
        return listOf(
            make("Today", EventRepeat.DAY, today, today.plusDays(1), 4, true, false),
            make("This week", EventRepeat.WEEK, weekStart, weekStart.plusWeeks(1), 3, false, false),
            make("This month", EventRepeat.MONTH, monthStart, monthStart.plusMonths(1), 7, false, true),
            make("This year", EventRepeat.YEAR, yearStart, yearStart.plusYears(1), 1, true, false),
        )
    }
}

object EventTemplates {
    /** The eight starting points of the first-run wizard (our own wording). */
    val starters: List<EventStarter> = listOf(
        EventStarter("birthday", "🎂", "A birthday", EventKind.COUNTDOWN, "Birthday"),
        EventStarter("goal", "🎯", "A personal goal", EventKind.COUNTDOWN, "My goal"),
        EventStarter("fresh", "🌱", "Time since a fresh start", EventKind.SINCE, "Fresh start"),
        EventStarter("retire", "🏖️", "Counting to retirement", EventKind.COUNTDOWN, "Retirement"),
        EventStarter("together", "💞", "Our time together", EventKind.SINCE, "Together"),
        EventStarter("trip", "✈️", "A trip", EventKind.COUNTDOWN, "Trip"),
        EventStarter("other", "✨", "Something else", EventKind.COUNTDOWN, ""),
        EventStarter("ideas", "💡", "Give me ideas", EventKind.COUNTDOWN, "", ideas = true),
    )

    private fun t(e: String, n: String, k: EventKind, d: EventDesign, p: Int) = EventTemplate(e, n, k, d, p)
    private val CD = EventKind.COUNTDOWN
    private val SI = EventKind.SINCE
    private val TM = EventKind.TIMER

    /** Around sixty ideas of our own. */
    val ideas: List<EventTemplate> = listOf(
        t("🎂", "Birthday party", CD, EventDesign.FONTY, 2),
        t("🎁", "Gift day", CD, EventDesign.MINIMAL, 6),
        t("💍", "Wedding day", CD, EventDesign.SWISS, 6),
        t("💞", "Anniversary", SI, EventDesign.FONTY, 1),
        t("👶", "Baby due", CD, EventDesign.SWISS, 5),
        t("🍼", "Baby's age", SI, EventDesign.FONTY, 7),
        t("🎓", "Graduation", CD, EventDesign.MINIMAL, 8),
        t("📚", "Exam day", CD, EventDesign.GRID, 4),
        t("📝", "Assignment due", CD, EventDesign.ROW, 3),
        t("💼", "First day at work", CD, EventDesign.MINIMAL, 4),
        t("🚀", "Product launch", CD, EventDesign.SWISS, 8),
        t("📊", "Project deadline", CD, EventDesign.ROW, 9),
        t("🧾", "Tax deadline", CD, EventDesign.FONTY, 7),
        t("🏖️", "Retirement", CD, EventDesign.SWISS, 0),
        t("✈️", "Flight", CD, EventDesign.MINIMAL, 3),
        t("🏝️", "Island holiday", CD, EventDesign.SWISS, 3),
        t("🏕️", "Camping weekend", CD, EventDesign.GRID, 5),
        t("🚢", "Cruise", CD, EventDesign.ROW, 3),
        t("🎢", "Theme park day", CD, EventDesign.FONTY, 2),
        t("🏔️", "Mountain hike", CD, EventDesign.SWISS, 5),
        t("🎪", "Festival", CD, EventDesign.GRID, 8),
        t("🎵", "Concert", CD, EventDesign.MINIMAL, 9),
        t("🎬", "Movie night", CD, EventDesign.ROW, 9),
        t("⚽", "Big match", CD, EventDesign.SWISS, 4),
        t("🏆", "Tournament", CD, EventDesign.GRID, 0),
        t("🏃", "Race day", CD, EventDesign.SWISS, 2),
        t("🏋️", "Gym streak", SI, EventDesign.FONTY, 4),
        t("🧘", "Meditation habit", SI, EventDesign.MINIMAL, 5),
        t("🚭", "Smoke free", SI, EventDesign.GRID, 5),
        t("🍷", "Alcohol free", SI, EventDesign.FONTY, 7),
        t("🍬", "Sugar free", SI, EventDesign.ROW, 6),
        t("📵", "Screen detox", SI, EventDesign.MINIMAL, 9),
        t("💤", "Early nights", SI, EventDesign.FONTY, 9),
        t("🥗", "Eating well", SI, EventDesign.SWISS, 5),
        t("💧", "Water goal", TM, EventDesign.SWISS, 3),
        t("🍅", "Focus session", TM, EventDesign.SWISS, 1),
        t("🍳", "Cooking timer", TM, EventDesign.MINIMAL, 2),
        t("🧺", "Laundry", TM, EventDesign.FONTY, 7),
        t("🧹", "Spring clean", CD, EventDesign.ROW, 5),
        t("🪴", "Plant watering", TM, EventDesign.MINIMAL, 5),
        t("🐶", "Puppy's age", SI, EventDesign.FONTY, 0),
        t("🐱", "Cat adoption day", SI, EventDesign.FONTY, 6),
        t("🏡", "Moving day", CD, EventDesign.SWISS, 2),
        t("🔑", "House keys day", SI, EventDesign.MINIMAL, 4),
        t("🚗", "Driving test", CD, EventDesign.MINIMAL, 3),
        t("🛠️", "Car service", CD, EventDesign.FONTY, 7),
        t("🩺", "Check-up", CD, EventDesign.MINIMAL, 7),
        t("💊", "Medicine course", CD, EventDesign.ROW, 1),
        t("🦷", "Dentist", CD, EventDesign.FONTY, 7),
        t("🎄", "Christmas", CD, EventDesign.GRID, 1),
        t("🎆", "New Year", CD, EventDesign.SWISS, 9),
        t("🎃", "Halloween", CD, EventDesign.FONTY, 2),
        t("🌸", "Spring", CD, EventDesign.SWISS, 6),
        t("☀️", "Summer break", CD, EventDesign.ROW, 0),
        t("🍂", "Autumn", CD, EventDesign.FONTY, 2),
        t("❄️", "First snow", CD, EventDesign.MINIMAL, 3),
        t("📖", "Finish the book", CD, EventDesign.ROW, 5),
        t("🎨", "Art challenge", CD, EventDesign.GRID, 8),
        t("🌍", "Move abroad", CD, EventDesign.SWISS, 3),
        t("🤝", "Friend reunion", CD, EventDesign.FONTY, 6),
        t("🎤", "Open mic night", CD, EventDesign.MINIMAL, 8),
    )

    fun byName(name: String): EventTemplate? = ideas.firstOrNull { it.name == name }
}
