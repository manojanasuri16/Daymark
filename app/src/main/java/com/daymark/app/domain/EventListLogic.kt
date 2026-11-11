package com.daymark.app.domain

import java.time.ZoneId

/** Pure filtering and sorting for the Events tab. */
object EventListLogic {
    /** Completed = a non-repeating event whose end has passed. Repeating and open-ended events are always ongoing. */
    fun isCompleted(e: CountEvent, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        EventEngine.progress(e, now, zone).completed

    fun isOngoing(e: CountEvent, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean = !isCompleted(e, now, zone)

    /** Archived events show only under [EventFilter.ARCHIVED]; every other filter hides them. */
    fun filter(events: List<CountEvent>, filter: EventFilter, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<CountEvent> =
        when (filter) {
            EventFilter.ARCHIVED -> events.filter { it.archived }
            EventFilter.ALL -> events.filter { !it.archived }
            EventFilter.ONGOING -> events.filter { !it.archived && isOngoing(it, now, zone) }
            EventFilter.COMPLETED -> events.filter { !it.archived && isCompleted(it, now, zone) }
        }

    /** Number of events each filter would show (for the "(none)" labels). */
    fun counts(events: List<CountEvent>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Map<EventFilter, Int> =
        EventFilter.entries.associateWith { filter(events, it, now, zone).size }

    /**
     * [asc] is the natural order of the key: A to Z, soonest first, lowest percent first, oldest first.
     * For "Upcoming events" events that have no coming end (open-ended or already finished) always go last.
     */
    fun sort(events: List<CountEvent>, sort: EventSort, asc: Boolean, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<CountEvent> {
        fun <T : Comparable<T>> by(key: (CountEvent) -> T): List<CountEvent> {
            val c = compareBy<CountEvent> { key(it) }.thenBy { it.id }
            return events.sortedWith(if (asc) c else c.reversed())
        }
        return when (sort) {
            EventSort.NAME -> by { it.name.lowercase() }
            EventSort.CREATED -> by { it.createdAt }
            EventSort.MODIFIED -> by { it.modifiedAt }
            EventSort.PERCENT -> by { EventEngine.progress(it, now, zone).fraction ?: 0f }
            EventSort.UPCOMING -> {
                val (upcoming, rest) = events.partition {
                    val p = EventEngine.progress(it, now, zone)
                    p.end != null && !p.completed
                }
                val key = { e: CountEvent -> EventEngine.progress(e, now, zone).remainingMs ?: Long.MAX_VALUE }
                val c = compareBy<CountEvent> { key(it) }.thenBy { it.id }
                upcoming.sortedWith(if (asc) c else c.reversed()) + rest.sortedBy { it.id }
            }
        }
    }
}
