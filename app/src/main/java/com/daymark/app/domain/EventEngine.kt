package com.daymark.app.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** One run of an event: [start] to [end] (null when open-ended). [index] counts cycles from 0. */
data class Cycle(val start: Long, val end: Long?, val index: Int)

data class EventProgress(
    val start: Long,
    val end: Long?,
    val elapsedMs: Long,
    val remainingMs: Long?,
    val totalMs: Long?,
    /** 0..1, or null when open-ended. */
    val fraction: Float?,
    val completed: Boolean,
)

data class EventText(val primary: String, val detailDate: String)

/** Calendar-correct difference split into Y M D H min (whole units). */
data class UnitBreakdown(val years: Long, val months: Long, val days: Long, val hours: Long, val minutes: Long) {
    fun asList() = listOf(years, months, days, hours, minutes)
}

/** Pure event maths. Everything takes `now` and a zone so it is deterministic. */
object EventEngine {
    private const val MAX_LOOP = 1_000_000

    // region Cycles

    /** Timer end: start plus the calendar duration (years/months/days follow the calendar, hours/minutes are exact). */
    fun addDuration(startMs: Long, y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId): Long {
        val z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startMs), zone)
            .plusYears(y.toLong()).plusMonths(m.toLong()).plusDays(d.toLong())
        return z.toInstant().plusSeconds(h * 3600L + min * 60L).toEpochMilli()
    }

    private fun baseEnd(e: CountEvent, zone: ZoneId): Long? = when (e.kind) {
        EventKind.TIMER -> addDuration(e.startAt, e.timerYears, e.timerMonths, e.timerDays, e.timerHours, e.timerMinutes, zone)
        else -> e.endAt
    }

    private fun shift(ms: Long, repeat: EventRepeat, k: Long, zone: ZoneId): Long {
        if (k == 0L) return ms
        val z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
        val r = when (repeat) {
            EventRepeat.DAY -> z.plusDays(k)
            EventRepeat.WEEK -> z.plusWeeks(k)
            EventRepeat.TWO_WEEKS -> z.plusWeeks(2 * k)
            EventRepeat.MONTH -> z.plusMonths(k)
            EventRepeat.YEAR -> z.plusYears(k)
            else -> z
        }
        return r.toInstant().toEpochMilli()
    }

    private fun unitOf(repeat: EventRepeat): Pair<ChronoUnit, Long> = when (repeat) {
        EventRepeat.DAY -> ChronoUnit.DAYS to 1L
        EventRepeat.WEEK -> ChronoUnit.WEEKS to 1L
        EventRepeat.TWO_WEEKS -> ChronoUnit.WEEKS to 2L
        EventRepeat.MONTH -> ChronoUnit.MONTHS to 1L
        else -> ChronoUnit.YEARS to 1L
    }

    /** The cycle with the given [index] (index only matters for repeating events). */
    fun cycleAt(e: CountEvent, index: Int, zone: ZoneId): Cycle {
        val end0 = baseEnd(e, zone)
        val rep = effectiveRepeat(e)
        return when {
            rep == EventRepeat.NONE || index <= 0 -> Cycle(e.startAt, end0, 0)
            rep == EventRepeat.ON_COMPLETION -> {
                var s = e.startAt
                var en = end0 ?: return Cycle(e.startAt, null, 0)
                for (i in 0 until index) {
                    s = en
                    en = addDuration(s, e.timerYears, e.timerMonths, e.timerDays, e.timerHours, e.timerMinutes, zone)
                    if (en <= s) return Cycle(e.startAt, end0, 0)
                }
                Cycle(s, en, index)
            }
            else -> Cycle(
                shift(e.startAt, rep, index.toLong(), zone),
                end0?.let { shift(it, rep, index.toLong(), zone) },
                index,
            )
        }
    }

    private fun effectiveRepeat(e: CountEvent): EventRepeat = when {
        e.repeat == EventRepeat.ON_COMPLETION && e.kind != EventKind.TIMER -> EventRepeat.NONE
        e.repeat != EventRepeat.NONE && baseEnd(e, ZoneId.of("UTC")) == null && e.kind != EventKind.SINCE -> EventRepeat.NONE
        else -> e.repeat
    }

    /**
     * The cycle that is running (or, for repeating events between runs, the next one).
     * A repeating event never completes; a plain event stays on its only cycle.
     */
    fun cycle(e: CountEvent, now: Long, zone: ZoneId = ZoneId.systemDefault()): Cycle {
        val repeat = effectiveRepeat(e)
        val end0 = baseEnd(e, zone)
        if (repeat == EventRepeat.NONE || end0 == null) {
            // Open-ended repeating since events restart at the next anniversary of the start.
            if (repeat != EventRepeat.NONE && repeat != EventRepeat.ON_COMPLETION && end0 == null && now >= e.startAt) {
                return openEndedCycle(e, repeat, now, zone)
            }
            return Cycle(e.startAt, end0, 0)
        }
        if (repeat == EventRepeat.ON_COMPLETION) {
            var s = e.startAt
            var en: Long = end0
            var k = 0
            if (en <= s) return Cycle(s, en, 0)
            while (now >= en && k < MAX_LOOP) {
                s = en
                en = addDuration(s, e.timerYears, e.timerMonths, e.timerDays, e.timerHours, e.timerMinutes, zone)
                k++
                if (en <= s) break
            }
            return Cycle(s, en, k)
        }
        if (now < end0) return Cycle(e.startAt, end0, 0)
        val (unit, step) = unitOf(repeat)
        val endZ = ZonedDateTime.ofInstant(Instant.ofEpochMilli(end0), zone)
        val nowZ = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        var k = maxOf(0L, unit.between(endZ, nowZ) / step - 1)
        var guard = 0
        while (shift(end0, repeat, k, zone) <= now && guard++ < 10_000) k++
        return Cycle(shift(e.startAt, repeat, k, zone), shift(end0, repeat, k, zone), k.toInt())
    }

    private fun openEndedCycle(e: CountEvent, repeat: EventRepeat, now: Long, zone: ZoneId): Cycle {
        val (unit, step) = unitOf(repeat)
        val sZ = ZonedDateTime.ofInstant(Instant.ofEpochMilli(e.startAt), zone)
        val nowZ = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        var k = maxOf(0L, unit.between(sZ, nowZ) / step)
        var guard = 0
        while (shift(e.startAt, repeat, k, zone) > now && k > 0 && guard++ < 100) k--
        return Cycle(shift(e.startAt, repeat, k, zone), null, k.toInt())
    }

    fun progress(e: CountEvent, now: Long, zone: ZoneId = ZoneId.systemDefault()): EventProgress {
        val c = cycle(e, now, zone)
        val elapsed = (now - c.start).coerceAtLeast(0L)
        val end = c.end ?: return EventProgress(c.start, null, elapsed, null, null, null, false)
        val total = (end - c.start).coerceAtLeast(0L)
        val remaining = (end - now).coerceAtLeast(0L)
        val repeating = effectiveRepeat(e) != EventRepeat.NONE
        val completed = !repeating && now >= end
        val fraction = when {
            completed -> 1f
            total == 0L -> 1f
            else -> (elapsed.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
        }
        return EventProgress(c.start, end, minOf(elapsed, total), remaining, total, fraction, completed)
    }

    // endregion

    // region Units

    private val ORDER = listOf(CountUnit.MINUTES, CountUnit.HOURS, CountUnit.DAYS, CountUnit.WEEKS, CountUnit.MONTHS, CountUnit.YEARS)

    private fun chrono(u: CountUnit) = when (u) {
        CountUnit.MINUTES -> ChronoUnit.MINUTES
        CountUnit.HOURS -> ChronoUnit.HOURS
        CountUnit.DAYS -> ChronoUnit.DAYS
        CountUnit.WEEKS -> ChronoUnit.WEEKS
        CountUnit.MONTHS -> ChronoUnit.MONTHS
        CountUnit.YEARS -> ChronoUnit.YEARS
    }

    private fun zdt(ms: Long, zone: ZoneId) = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)

    /** Calendar-correct Y M D H min between two instants (a <= b). Seconds are dropped. */
    fun breakdown(a: Long, b: Long, zone: ZoneId = ZoneId.systemDefault()): UnitBreakdown {
        if (b <= a) return UnitBreakdown(0, 0, 0, 0, 0)
        val za = zdt(a, zone)
        val zb = zdt(b, zone)
        val y = ChronoUnit.YEARS.between(za, zb)
        var t = za.plusYears(y)
        val m = ChronoUnit.MONTHS.between(t, zb)
        t = t.plusMonths(m)
        val d = ChronoUnit.DAYS.between(t, zb)
        t = t.plusDays(d)
        val h = ChronoUnit.HOURS.between(t, zb)
        t = t.plusHours(h)
        val min = ChronoUnit.MINUTES.between(t, zb)
        return UnitBreakdown(y, m, d, h, min)
    }

    /** [n] (2..5) leading units of the breakdown, optionally rounded up at the last shown unit without carry bugs. */
    fun breakdownRounded(a: Long, b: Long, n: Int, roundUp: Boolean, zone: ZoneId = ZoneId.systemDefault()): UnitBreakdown {
        val count = n.coerceIn(1, 5)
        val bd = breakdown(a, b, zone)
        val parts = bd.asList()
        val kept = parts.mapIndexed { i, v -> if (i < count) v else 0L }
        var shown = UnitBreakdown(kept[0], kept[1], kept[2], kept[3], kept[4])
        if (!roundUp) return shown
        // Anything left below the last shown unit (including seconds) rounds the last unit up.
        val za = zdt(a, zone)
        var t = za.plusYears(shown.years).plusMonths(shown.months).plusDays(shown.days).plusHours(shown.hours).plusMinutes(shown.minutes)
        if (t.toInstant().toEpochMilli() >= b) return shown
        t = when (count) {
            1 -> t.plusYears(1)
            2 -> t.plusMonths(1)
            3 -> t.plusDays(1)
            4 -> t.plusHours(1)
            else -> t.plusMinutes(1)
        }
        val b2 = t.toInstant().toEpochMilli()
        val bd2 = breakdown(a, b2, zone).asList()
        val kept2 = bd2.mapIndexed { i, v -> if (i < count) v else 0L }
        shown = UnitBreakdown(kept2[0], kept2[1], kept2[2], kept2[3], kept2[4])
        return shown
    }

    /** Fractional amount of [unit] between a and b (a <= b), calendar-aware. */
    fun unitValue(a: Long, b: Long, unit: CountUnit, zone: ZoneId = ZoneId.systemDefault()): Double {
        if (b <= a) return 0.0
        val za = zdt(a, zone)
        val zb = zdt(b, zone)
        val cu = chrono(unit)
        val whole = cu.between(za, zb)
        val base = za.plus(whole, cu)
        val next = za.plus(whole + 1, cu)
        val span = next.toInstant().toEpochMilli() - base.toInstant().toEpochMilli()
        val rest = b - base.toInstant().toEpochMilli()
        val frac = if (span <= 0) 0.0 else rest.toDouble() / span
        return whole + frac.coerceIn(0.0, 0.999999999)
    }

    private fun smaller(u: CountUnit): CountUnit? = when (u) {
        CountUnit.YEARS -> CountUnit.MONTHS
        CountUnit.MONTHS -> CountUnit.DAYS
        CountUnit.WEEKS -> CountUnit.DAYS
        CountUnit.DAYS -> CountUnit.HOURS
        CountUnit.HOURS -> CountUnit.MINUTES
        CountUnit.MINUTES -> null
    }

    /** Whole amount and unit to show for a single-unit display. */
    fun singleAmount(a: Long, b: Long, unit: CountUnit, roundUp: Boolean, precision: Boolean, zone: ZoneId): Pair<Long, CountUnit> {
        var u = unit
        var v = unitValue(a, b, u, zone)
        while (precision && v < 2.0) {
            val s = smaller(u) ?: break
            u = s
            v = unitValue(a, b, u, zone)
        }
        val whole = if (roundUp) ceil(v - 1e-9) else floor(v + 1e-9)
        return whole.toLong().coerceAtLeast(0) to u
    }

    fun unitName(u: CountUnit, amount: Long): String {
        val base = when (u) {
            CountUnit.MINUTES -> "minute"
            CountUnit.HOURS -> "hour"
            CountUnit.DAYS -> "day"
            CountUnit.WEEKS -> "week"
            CountUnit.MONTHS -> "month"
            CountUnit.YEARS -> "year"
        }
        return if (amount == 1L) base else base + "s"
    }

    private fun multiText(b: UnitBreakdown, n: Int): String {
        val labels = listOf("y", "mo", "d", "h", "m")
        val vals = b.asList()
        val last = n.coerceIn(1, 5) - 1
        val first = (0..last).firstOrNull { vals[it] != 0L } ?: last
        return (first..last).joinToString(" ") { "${vals[it]}${labels[it]}" }
    }

    // endregion

    // region Text

    fun accessoryText(acc: Accessory, amount: String, down: Boolean): String {
        val a = if (acc == Accessory.AUTO) (if (down) Accessory.LEFT else Accessory.PASSED) else acc
        return when (a) {
            Accessory.LEFT -> "$amount left"
            Accessory.IN -> "In $amount"
            Accessory.PASSED -> "$amount passed"
            Accessory.AGO -> "$amount ago"
            else -> amount
        }
    }

    fun percentOf(p: EventProgress): Int? {
        val f = p.fraction ?: return null
        return if (p.completed) 100 else floor(f * 100.0 + 1e-6).toInt().coerceIn(0, 100)
    }

    /**
     * Text of an event card. [precision] is the global "Precision Countdown" setting; it is combined with the event's own flag.
     */
    fun format(
        e: CountEvent,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        precision: Boolean = true,
        locale: Locale = Locale.getDefault(),
    ): EventText {
        val p = progress(e, now, zone)
        val o = e.options
        val c = cycle(e, now, zone)
        val down = !e.countUp && p.end != null
        val detail = detailDate(if (down) p.end!! else p.start, now, zone, locale)
        val pct = percentOf(p)
        if (o.showPercent && pct != null) {
            val primary = if (o.accessory == Accessory.LEFT) "${100 - pct}% left" else "$pct%"
            return EventText(primary, detail)
        }
        if (down && p.completed) return EventText("Completed", detail)
        val a: Long
        val b: Long
        if (down) { a = now.coerceAtLeast(c.start); b = p.end!! } else { a = p.start; b = now.coerceAtLeast(p.start) }
        val amount = if (o.multiUnits in 2..5) {
            multiText(breakdownRounded(a, b, o.multiUnits, o.roundUpFor(!down), zone), o.multiUnits)
        } else {
            val (n, u) = singleAmount(a, b, o.singleUnit, o.roundUpFor(!down), o.precision && precision, zone)
            "$n ${unitName(u, n)}"
        }
        return EventText(accessoryText(o.accessory, amount, down), detail)
    }

    fun detailDate(ms: Long, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
        val d = zdt(ms, zone).toLocalDate()
        if (d == zdt(now, zone).toLocalDate()) return "Today"
        return DateTimeFormatter.ofPattern("dd MMM yyyy", locale).format(d)
    }

    // endregion

    // region Ordering and notifications

    /** Ongoing (not archived, not completed, with an end) events, nearest to finish first. Returns the [n]th (0 = nearest). */
    fun nextEvent(events: List<CountEvent>, now: Long, n: Int, zone: ZoneId = ZoneId.systemDefault()): CountEvent? =
        events.asSequence()
            .filter { !it.archived }
            .mapNotNull { e -> progress(e, now, zone).takeIf { !it.completed && it.remainingMs != null }?.let { e to it.remainingMs!! } }
            .sortedWith(compareBy({ it.second }, { it.first.id }))
            .map { it.first }
            .drop(n.coerceAtLeast(0))
            .firstOrNull()

    /** Upcoming notification moments (after [now]) for the current and next cycle, oldest first. Callers filter by the user's switches. */
    fun notificationTimes(e: CountEvent, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<Long, NotifType>> {
        if (e.archived) return emptyList()
        val repeating = effectiveRepeat(e) != EventRepeat.NONE
        val first = cycle(e, now, zone)
        val cycles = if (repeating) listOf(first, cycleAt(e, first.index + 1, zone)) else listOf(first)
        val out = ArrayList<Pair<Long, NotifType>>()
        val up = e.countUp || e.kind == EventKind.SINCE
        for (c in cycles) {
            val end = c.end
            if (end != null) {
                if (end > now) out += end to NotifType.COMPLETED
                val day = zdt(end, zone).minusDays(1).toInstant().toEpochMilli()
                if (day > now) out += day to NotifType.ONE_DAY_BEFORE
                val week = zdt(end, zone).minusWeeks(1).toInstant().toEpochMilli()
                if (week > now) out += week to NotifType.ONE_WEEK_BEFORE
            }
            if (up) {
                val s = zdt(c.start, zone)
                fun next(unit: ChronoUnit, type: NotifType) {
                    var k = maxOf(1L, unit.between(s, zdt(now, zone)))
                    var guard = 0
                    while (s.plus(k, unit).toInstant().toEpochMilli() <= now && guard++ < 10_000) k++
                    val t = s.plus(k, unit).toInstant().toEpochMilli()
                    if (end == null || t < end) out += t to type
                }
                next(ChronoUnit.DAYS, NotifType.DAILY_AFTER)
                next(ChronoUnit.WEEKS, NotifType.WEEKLY_AFTER)
                next(ChronoUnit.MONTHS, NotifType.MONTHLY_AFTER)
                next(ChronoUnit.YEARS, NotifType.YEARLY_AFTER)
            }
        }
        return out.distinct().sortedBy { it.first }
    }

    // endregion
}
