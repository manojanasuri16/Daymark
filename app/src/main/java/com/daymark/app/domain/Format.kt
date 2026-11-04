package com.daymark.app.domain

object Format {
    fun days(d: Long): String = if (d == 1L) "1 day" else "$d days"

    /** e.g. "12d 4h" / "4h 12m" / "12m" — compact human duration. */
    fun compact(millis: Long): String {
        val totalMin = millis / 60_000
        val d = totalMin / 1440
        val h = (totalMin % 1440) / 60
        val m = totalMin % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun decimalDays(days: Double): String = when {
        days >= 10 -> "%.0f".format(days)
        else -> "%.1f".format(days)
    }
}
