package com.daymark.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daymark.app.data.RelapseEntity
import com.daymark.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Seeds a realistic 4-month journey for testing and store screenshots. Debug source set only. */
class DemoSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = context.container
                val day = 86_400_000L
                val hour = 3_600_000L
                val now = System.currentTimeMillis()
                val habits = listOf(
                    com.daymark.app.data.HabitEntity(id = 1, name = "Alcohol", emoji = "🌊", startAt = now - 120 * day - 5 * hour, sortOrder = 0),
                    com.daymark.app.data.HabitEntity(id = 2, name = "Smoking", emoji = "🚭", startAt = now - 41 * day - 3 * hour, sortOrder = 1),
                    com.daymark.app.data.HabitEntity(id = 3, name = "Doomscrolling", emoji = "📵", startAt = now - 9 * day - 7 * hour, sortOrder = 2),
                )
                val entries = listOf(
                    Triple(118, "Stress|Late night", 2) to "Alone at home",
                    Triple(109, "Social pressure|Celebration", 3) to "Party / event",
                    Triple(97, "Boredom|Screens", 2) to "Alone at home",
                    Triple(90, "Stress|Conflict", 1) to "At work",
                    Triple(74, "Loneliness|Late night", 2) to "Alone at home",
                    Triple(52, "Celebration|Social pressure", 4) to "With friends",
                ).map { (t, situation) ->
                    RelapseEntity(
                        timestamp = now - t.first * day - (2 + t.first % 7) * hour,
                        triggers = t.second, mood = t.third, situation = situation,
                        notes = if (t.first == 52) "Birthday dinner. Next time: plan an alcohol-free drink and leave earlier." else "",
                    )
                }
                val smoking = listOf(33 to "Stress", 19 to "Social pressure|Cravings").map { (d, t) ->
                    RelapseEntity(habitId = 2, timestamp = now - d * day - 4 * hour, triggers = t, mood = 2, situation = "With friends")
                }
                c.habits.replaceAll(habits, entries + smoking)
                c.settings.completeOnboarding(1)
                WidgetUpdater.updateAll(context)
            } finally { pending.finish() }
        }
    }
}
