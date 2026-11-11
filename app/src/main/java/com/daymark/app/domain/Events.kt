package com.daymark.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What an event counts. Ids are stored in the database; never renumber. */
enum class EventKind(val id: Int) {
    COUNTDOWN(0), SINCE(1), TIMER(2);

    companion object {
        fun of(id: Int) = entries.firstOrNull { it.id == id } ?: COUNTDOWN
    }
}

/** How a cycle repeats. [ON_COMPLETION] (timers only) restarts the timer the moment it ends. */
enum class EventRepeat(val id: Int) {
    NONE(0), DAY(1), WEEK(2), TWO_WEEKS(3), MONTH(4), YEAR(5), ON_COMPLETION(6);

    companion object {
        fun of(id: Int) = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** Units a single-unit display can use, smallest first. */
enum class CountUnit { MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

/** Words after (or before) the amount, e.g. "7 days left", "In 7 days", "7 days passed", "7 days ago". [AUTO] picks left/passed. */
enum class Accessory { AUTO, LEFT, IN, PASSED, AGO, NONE }

enum class EventDesign { FONTY, SWISS, GRID, ROW, MINIMAL }
enum class EventAlign { START, CENTER, END }
enum class ItemSize { SMALL, MEDIUM, LARGE }
enum class ItemShape { SQUARE, CIRCLE, ROUNDED }
enum class FutureStyle { OUTLINED, FADED, HIDDEN }
enum class TileShape { SQUARE, ROUNDED, CIRCLE }

/** List sorting and filtering for the Events tab. */
enum class EventSort { NAME, UPCOMING, PERCENT, CREATED, MODIFIED }
enum class EventFilter { ONGOING, COMPLETED, ALL, ARCHIVED }

/** Which notification a planned time belongs to. */
enum class NotifType { COMPLETED, ONE_DAY_BEFORE, ONE_WEEK_BEFORE, DAILY_AFTER, WEEKLY_AFTER, MONTHLY_AFTER, YEARLY_AFTER }

/**
 * Counting and style options of one event, stored as ONE JSON column so new options never need a migration.
 * Decoding ignores unknown keys and defaults missing/invalid ones (see [decode]).
 */
@Serializable
data class EventOptions(
    // counting
    val showPercent: Boolean = false,
    /** 0 = a single unit ([singleUnit]); 2..5 = that many of Y M D H min. */
    val multiUnits: Int = 0,
    val singleUnit: CountUnit = CountUnit.DAYS,
    /** null = default by direction (round up what is left, round down what has passed). */
    val roundUp: Boolean? = null,
    val accessory: Accessory = Accessory.AUTO,
    /** Show a smaller unit when the amount is below 2 ("36 hours left", not "1.5 days left"). */
    val precision: Boolean = true,
    // timer duration (TIMER only)
    val timerYears: Int = 0,
    val timerMonths: Int = 0,
    val timerDays: Int = 7,
    val timerHours: Int = 0,
    val timerMinutes: Int = 0,
    // style
    val design: EventDesign = EventDesign.FONTY,
    val bg: Int = EventDefaults.presets[0].bg,
    val accent: Int = EventDefaults.presets[0].accent,
    val text: Int = EventDefaults.presets[0].text,
    val fontId: String = "inter",
    val clockwise: Boolean = true,
    val align: EventAlign = EventAlign.START,
    val showDetails: Boolean = true,
    val matchItems: Boolean = false,
    val itemSize: ItemSize = ItemSize.MEDIUM,
    val itemShape: ItemShape = ItemShape.SQUARE,
    val futureStyle: FutureStyle = FutureStyle.OUTLINED,
    val showIcon: Boolean = true,
    val icon: String = "star",
    val iconColor: Int = -1,
    val barSize: ItemSize = ItemSize.SMALL,
    val showBar: Boolean = true,
    val shape: TileShape = TileShape.SQUARE,
    // privacy
    val showNameOutside: Boolean = true,
) {
    fun roundUpFor(countUp: Boolean): Boolean = roundUp ?: !countUp

    fun encode(): String = runCatching { JSON.encodeToString(serializer(), this) }.getOrDefault("{}")

    companion object {
        private val JSON = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = false
            explicitNulls = false
            isLenient = true
        }

        /** Never throws: anything unreadable gives the defaults. */
        fun decode(json: String?): EventOptions {
            if (json.isNullOrBlank()) return EventOptions()
            return runCatching { JSON.decodeFromString(serializer(), json) }.getOrDefault(EventOptions())
        }
    }
}

data class CountEvent(
    val id: Long = 0,
    val name: String,
    val kind: EventKind,
    val startAt: Long,
    /** null for an open-ended time-since event. For a timer this mirrors start + duration. */
    val endAt: Long? = null,
    val allDay: Boolean = true,
    val repeat: EventRepeat = EventRepeat.NONE,
    /** Display direction: false counts what is left, true counts what has passed. */
    val countUp: Boolean = kind == EventKind.SINCE,
    val notes: String = "",
    val archived: Boolean = false,
    val createdAt: Long = 0,
    val modifiedAt: Long = 0,
    val options: EventOptions = EventOptions(),
) {
    val timerYears get() = options.timerYears
    val timerMonths get() = options.timerMonths
    val timerDays get() = options.timerDays
    val timerHours get() = options.timerHours
    val timerMinutes get() = options.timerMinutes
}
