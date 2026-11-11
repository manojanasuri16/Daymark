package com.daymark.app.domain

import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One notification the planner wants to show: [at] is when it falls due. */
data class PlannedNotification(val eventId: Long, val type: NotifType, val at: Long) {
    /** Stable identity used to remember what was already shown. */
    val key: String get() = "$eventId:${type.name}:$at"
}

/** Title and body of an event notification. */
data class EventNotificationText(val title: String, val body: String)

/** Pure planning and wording of event notifications (no Android). */
object EventNotificationPlanner {
    /** A notification that fell due up to this long ago may still be shown; older ones are dropped (no floods). */
    const val GRACE_MS = 60 * 60_000L

    private fun priority(t: NotifType) = when (t) {
        NotifType.YEARLY_AFTER -> 0
        NotifType.MONTHLY_AFTER -> 1
        NotifType.WEEKLY_AFTER -> 2
        NotifType.DAILY_AFTER -> 3
        else -> 4
    }

    /**
     * Every enabled notification due in `(now - GRACE_MS, ...)` that is not in [alreadySent], earliest first.
     * When several count-up notifications of one event land on the same moment (day 7 is also "1 week"),
     * only the most significant one is kept.
     */
    fun candidates(
        events: List<CountEvent>,
        now: Long,
        notifies: (NotifType) -> Boolean,
        alreadySent: (String) -> Boolean = { false },
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PlannedNotification> {
        val out = ArrayList<PlannedNotification>()
        for (e in events) {
            if (e.archived) continue
            val mine = EventEngine.notificationTimes(e, now - GRACE_MS, zone)
                .filter { notifies(it.second) }
                .map { PlannedNotification(e.id, it.second, it.first) }
            out += mine.groupBy { it.at }.values.map { g -> g.minBy { priority(it.type) } }
        }
        return out.filter { !alreadySent(it.key) }.sortedWith(compareBy({ it.at }, { it.eventId }, { it.type.ordinal }))
    }

    /** The moment the next notification falls due (never in the past: due-now items give [now]), or null. */
    fun nextTime(
        events: List<CountEvent>,
        now: Long,
        notifies: (NotifType) -> Boolean,
        alreadySent: (String) -> Boolean = { false },
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? = candidates(events, now, notifies, alreadySent, zone).firstOrNull()?.let { maxOf(it.at, now) }

    /** Notifications to show right now: due at or before [now] (and within the grace window). */
    fun due(
        events: List<CountEvent>,
        now: Long,
        notifies: (NotifType) -> Boolean,
        alreadySent: (String) -> Boolean = { false },
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PlannedNotification> = candidates(events, now, notifies, alreadySent, zone).filter { it.at <= now }

    /** Friendly wording. With [showName] false nothing names the event. */
    fun text(e: CountEvent, p: PlannedNotification, showName: Boolean, zone: ZoneId = ZoneId.systemDefault()): EventNotificationText {
        val name = e.name.ifBlank { "Your event" }
        if (!showName) {
            val body = when (p.type) {
                NotifType.COMPLETED -> "🎉 An event is complete"
                NotifType.ONE_DAY_BEFORE -> "An event is tomorrow"
                NotifType.ONE_WEEK_BEFORE -> "An event is in one week"
                else -> "An event reached a new milestone"
            }
            return EventNotificationText("Daymark", body)
        }
        val body = when (p.type) {
            NotifType.COMPLETED -> "🎉 $name is complete"
            NotifType.ONE_DAY_BEFORE -> "$name is tomorrow"
            NotifType.ONE_WEEK_BEFORE -> "$name is in one week"
            else -> {
                val start = EventEngine.cycle(e, p.at, zone).start
                val unit = when (p.type) {
                    NotifType.DAILY_AFTER -> ChronoUnit.DAYS
                    NotifType.WEEKLY_AFTER -> ChronoUnit.WEEKS
                    NotifType.MONTHLY_AFTER -> ChronoUnit.MONTHS
                    else -> ChronoUnit.YEARS
                }
                val s = java.time.Instant.ofEpochMilli(start).atZone(zone)
                // Add a minute of slack so the exact due moment counts as the whole unit.
                val n = unit.between(s, java.time.Instant.ofEpochMilli(p.at + 60_000L).atZone(zone)).coerceAtLeast(1)
                val label = when (unit) {
                    ChronoUnit.DAYS -> "day"; ChronoUnit.WEEKS -> "week"; ChronoUnit.MONTHS -> "month"; else -> "year"
                }
                "$name: $n $label${if (n == 1L) "" else "s"} so far"
            }
        }
        return EventNotificationText(name, body)
    }
}
