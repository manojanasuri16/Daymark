package com.daymark.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** The five fields of a timer's duration. */
enum class DurationField(val label: String, val max: Int) {
    YEARS("Years", 100), MONTHS("Months", 1200), DAYS("Days", 36_500), HOURS("Hours", 24_000), MINUTES("Minutes", 60_000)
}

/** One row of the repeat sheet: [enabled] is false when the event is longer than the period. */
data class RepeatOption(val repeat: EventRepeat, val enabled: Boolean)

/** Pure helpers behind the event editor (validation, repeat availability, text, colours, draft saving). */
object EventEditing {
    const val NAME_MAX = 80

    // region Timer duration

    fun clampField(field: DurationField, value: Int): Int = value.coerceIn(0, field.max)

    fun durationOf(o: EventOptions, f: DurationField): Int = when (f) {
        DurationField.YEARS -> o.timerYears
        DurationField.MONTHS -> o.timerMonths
        DurationField.DAYS -> o.timerDays
        DurationField.HOURS -> o.timerHours
        DurationField.MINUTES -> o.timerMinutes
    }

    fun withDuration(o: EventOptions, f: DurationField, value: Int): EventOptions {
        val v = clampField(f, value)
        return when (f) {
            DurationField.YEARS -> o.copy(timerYears = v)
            DurationField.MONTHS -> o.copy(timerMonths = v)
            DurationField.DAYS -> o.copy(timerDays = v)
            DurationField.HOURS -> o.copy(timerHours = v)
            DurationField.MINUTES -> o.copy(timerMinutes = v)
        }
    }

    /** A timer needs at least one minute, and every field inside its cap. */
    fun durationValid(o: EventOptions): Boolean =
        DurationField.entries.all { durationOf(o, it) in 0..it.max } && DurationField.entries.any { durationOf(o, it) > 0 }

    // endregion

    // region Validation and normalising

    /** Makes the stored end consistent with the kind (a timer's end is start + duration). */
    fun normalize(e: CountEvent, zone: ZoneId = ZoneId.systemDefault()): CountEvent = when (e.kind) {
        EventKind.TIMER -> e.copy(
            endAt = EventEngine.addDuration(e.startAt, e.timerYears, e.timerMonths, e.timerDays, e.timerHours, e.timerMinutes, zone),
        )
        else -> e
    }

    /** Whether the draft can be saved: a name, and an end after the start for countdowns and timers. */
    fun isValid(e: CountEvent, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (e.name.isBlank()) return false
        val n = normalize(e, zone)
        return when (e.kind) {
            EventKind.COUNTDOWN -> n.endAt != null && n.endAt > n.startAt
            EventKind.TIMER -> durationValid(e.options) && n.endAt != null && n.endAt > n.startAt
            EventKind.SINCE -> n.endAt == null || n.endAt > n.startAt
        }
    }

    // endregion

    /**
     * Switches a NEW draft to another [kind], keeping name, notes and style. Dates are re-derived: countdown and timer start now
     * and end a week later, a time-since event starts a week ago (or at its current start when that is already in the past).
     */
    fun convertKind(e: CountEvent, kind: EventKind, now: Long, zone: ZoneId = ZoneId.systemDefault()): CountEvent {
        if (e.kind == kind) return e
        val week = 7L * 24 * 60 * 60 * 1000
        val o = e.options.copy(accessory = Accessory.AUTO, roundUp = null, timerYears = 0, timerMonths = 0, timerDays = 7, timerHours = 0, timerMinutes = 0)
        val base = e.copy(kind = kind, repeat = EventRepeat.NONE, countUp = kind == EventKind.SINCE, options = o)
        return when (kind) {
            EventKind.COUNTDOWN -> base.copy(startAt = if (e.kind == EventKind.SINCE) now else e.startAt).let { it.copy(endAt = it.startAt + week) }
            EventKind.SINCE -> base.copy(startAt = if (e.startAt < now - 60_000) e.startAt else now - week, endAt = null)
            EventKind.TIMER -> normalize(base.copy(startAt = now), zone)
        }
    }

    // region Repeat

    private fun plusPeriod(ms: Long, repeat: EventRepeat, zone: ZoneId): Long? {
        val z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
        return when (repeat) {
            EventRepeat.DAY -> z.plusDays(1)
            EventRepeat.WEEK -> z.plusWeeks(1)
            EventRepeat.TWO_WEEKS -> z.plusWeeks(2)
            EventRepeat.MONTH -> z.plusMonths(1)
            EventRepeat.YEAR -> z.plusYears(1)
            else -> null
        }?.toInstant()?.toEpochMilli()
    }

    /** A period is available when one cycle fits inside it (end <= start + period). */
    fun repeatAvailable(repeat: EventRepeat, startMs: Long, endMs: Long?, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (repeat == EventRepeat.NONE || repeat == EventRepeat.ON_COMPLETION) return true
        if (endMs == null) return true
        val next = plusPeriod(startMs, repeat, zone) ?: return true
        return endMs <= next
    }

    /** Options for the repeat sheet. "On completion" is for timers only. */
    fun repeatOptions(e: CountEvent, zone: ZoneId = ZoneId.systemDefault()): List<RepeatOption> {
        val n = normalize(e, zone)
        val list = mutableListOf(EventRepeat.NONE, EventRepeat.DAY, EventRepeat.WEEK, EventRepeat.TWO_WEEKS, EventRepeat.MONTH, EventRepeat.YEAR)
        if (e.kind == EventKind.TIMER) list += EventRepeat.ON_COMPLETION
        return list.map { RepeatOption(it, repeatAvailable(it, n.startAt, n.endAt, zone)) }
    }

    fun repeatLabel(r: EventRepeat): String = when (r) {
        EventRepeat.NONE -> "No repeat"
        EventRepeat.DAY -> "Every day"
        EventRepeat.WEEK -> "Every week"
        EventRepeat.TWO_WEEKS -> "Every two weeks"
        EventRepeat.MONTH -> "Every month"
        EventRepeat.YEAR -> "Every year"
        EventRepeat.ON_COMPLETION -> "On completion"
    }

    /** If the current repeat no longer fits the (new) duration, fall back to no repeat. */
    fun fixRepeat(e: CountEvent, zone: ZoneId = ZoneId.systemDefault()): CountEvent {
        val n = normalize(e, zone)
        return if (repeatAvailable(e.repeat, n.startAt, n.endAt, zone)) e else e.copy(repeat = EventRepeat.NONE)
    }

    // endregion

    // region Dates and text

    /** Moves [ms] to [date], keeping its time of day. */
    fun withDate(ms: Long, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone).toLocalTime()).atZone(zone).toInstant().toEpochMilli()

    /** Moves [ms] to [time] on the same day. */
    fun withTime(ms: Long, time: LocalTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone).toLocalDate().atTime(time).atZone(zone).toInstant().toEpochMilli()

    fun startOfDay(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** Short span text such as "6d 12h 13m" (zero parts skipped). [seconds] adds a seconds part. */
    fun span(a: Long, b: Long, seconds: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        if (b <= a) return if (seconds) "0s" else "0m"
        val br = EventEngine.breakdown(a, b, zone)
        val parts = mutableListOf<String>()
        if (br.years > 0) parts += "${br.years}Y"
        if (br.months > 0) parts += "${br.months}M"
        if (br.days > 0) parts += "${br.days}d"
        if (br.hours > 0) parts += "${br.hours}h"
        if (br.minutes > 0) parts += "${br.minutes}m"
        val secs = ((b - a) / 1000) % 60
        if (seconds && (secs > 0 || parts.isEmpty())) parts += "${secs}s"
        return if (parts.isEmpty()) "0m" else parts.joinToString(" ")
    }

    /** "7 days" when the total is one whole unit, otherwise the short span. */
    fun totalText(a: Long, b: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val br = EventEngine.breakdown(a, b, zone)
        val nonZero = br.asList().count { it > 0 }
        if (nonZero == 1 && b > a) {
            val units = listOf(CountUnit.YEARS, CountUnit.MONTHS, CountUnit.DAYS, CountUnit.HOURS, CountUnit.MINUTES)
            val i = br.asList().indexOfFirst { it > 0 }
            val n = br.asList()[i]
            return "$n ${EventEngine.unitName(units[i], n)}"
        }
        return span(a, b, seconds = false, zone = zone)
    }

    /** True when the period [repeat] is shorter than the event, for the warning text. */
    fun unavailableRepeats(e: CountEvent, zone: ZoneId = ZoneId.systemDefault()): List<EventRepeat> =
        repeatOptions(e, zone).filter { !it.enabled }.map { it.repeat }

    // endregion

    // region Accessory choices

    /** Accessory options for the Counting tab, by display direction and mode. First entry of each pair is the stored value. */
    fun accessoryChoices(down: Boolean, percent: Boolean): List<Pair<Accessory, String>> = when {
        percent -> listOf(Accessory.LEFT to "… left", Accessory.NONE to "None")
        down -> listOf(Accessory.LEFT to "… left", Accessory.IN to "In …", Accessory.NONE to "None")
        else -> listOf(Accessory.PASSED to "… passed", Accessory.AGO to "… ago", Accessory.NONE to "None")
    }

    /** The accessory that is shown as selected (AUTO resolves to the direction's default). */
    fun effectiveAccessory(a: Accessory, down: Boolean, percent: Boolean): Accessory = when {
        percent -> if (a == Accessory.LEFT) Accessory.LEFT else Accessory.NONE
        a == Accessory.AUTO -> if (down) Accessory.LEFT else Accessory.PASSED
        down && (a == Accessory.PASSED || a == Accessory.AGO) -> Accessory.LEFT
        !down && (a == Accessory.LEFT || a == Accessory.IN) -> Accessory.PASSED
        else -> a
    }

    // endregion

    // region Colours

    /** "#RRGGBB" (alpha ignored). */
    fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** Parses "#RRGGBB", "RRGGBB" or "#AARRGGBB"; returns an opaque ARGB or null when invalid. */
    fun parseHex(text: String): Int? {
        val t = text.trim().removePrefix("#")
        if (t.length != 6 && t.length != 8) return null
        val v = t.toLongOrNull(16) ?: return null
        return (0xFF000000L or (v and 0xFFFFFF)).toInt()
    }

    /** Hue 0..360, saturation 0..1, value 0..1. */
    fun toHsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return floatArrayOf(h, if (max == 0f) 0f else d / max, max)
    }

    fun fromHsv(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val ss = s.coerceIn(0f, 1f)
        val vv = v.coerceIn(0f, 1f)
        val c = vv * ss
        val x = c * (1f - abs((hh / 60f) % 2f - 1f))
        val m = vv - c
        val (r, g, b) = when ((hh / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = Math.round((f + m) * 255f).coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    // endregion
}

/** JSON form of an editor draft, used so rotation (rememberSaveable) never loses edits. */
object EventDraftCodec {
    @Serializable
    private data class Dto(
        val id: Long, val name: String, val kind: Int, val startAt: Long, val endAt: Long? = null, val allDay: Boolean,
        val repeat: Int, val countUp: Boolean, val notes: String, val archived: Boolean, val createdAt: Long,
        val modifiedAt: Long, val options: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(e: CountEvent): String = json.encodeToString(
        Dto.serializer(),
        Dto(e.id, e.name, e.kind.id, e.startAt, e.endAt, e.allDay, e.repeat.id, e.countUp, e.notes, e.archived, e.createdAt, e.modifiedAt, e.options.encode()),
    )

    fun decode(text: String): CountEvent? = runCatching {
        val d = json.decodeFromString(Dto.serializer(), text)
        CountEvent(
            id = d.id, name = d.name, kind = EventKind.of(d.kind), startAt = d.startAt, endAt = d.endAt, allDay = d.allDay,
            repeat = EventRepeat.of(d.repeat), countUp = d.countUp, notes = d.notes, archived = d.archived,
            createdAt = d.createdAt, modifiedAt = d.modifiedAt, options = EventOptions.decode(d.options),
        )
    }.getOrNull()
}
