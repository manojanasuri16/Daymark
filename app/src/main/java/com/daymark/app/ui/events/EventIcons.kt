package com.daymark.app.ui.events

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.asAndroidPath
import java.util.concurrent.ConcurrentHashMap

data class EventIcon(val id: String, val label: String, val vector: () -> ImageVector)

/**
 * The fixed icon set of the Minimal design. Stored by id in [com.daymark.app.domain.EventOptions.icon].
 * Material Rounded icons are already a dependency; [draw] paints one onto a plain Canvas (widgets, bitmaps).
 */
object EventIcons {
    const val DEFAULT_ID = "star"

    val all: List<EventIcon> = listOf(
        EventIcon("star", "Star") { Icons.Rounded.Star },
        EventIcon("cake", "Cake") { Icons.Rounded.Cake },
        EventIcon("flight", "Flight") { Icons.Rounded.Flight },
        EventIcon("heart", "Heart") { Icons.Rounded.Favorite },
        EventIcon("work", "Work") { Icons.Rounded.Work },
        EventIcon("school", "School") { Icons.Rounded.School },
        EventIcon("fitness", "Fitness") { Icons.Rounded.FitnessCenter },
        EventIcon("home", "Home") { Icons.Rounded.Home },
        EventIcon("music", "Music") { Icons.Rounded.MusicNote },
        EventIcon("game", "Game") { Icons.Rounded.SportsEsports },
        EventIcon("movie", "Movie") { Icons.Rounded.Movie },
        EventIcon("gift", "Gift") { Icons.Rounded.CardGiftcard },
        EventIcon("camera", "Camera") { Icons.Rounded.CameraAlt },
        EventIcon("car", "Car") { Icons.Rounded.DirectionsCar },
        EventIcon("beach", "Beach") { Icons.Rounded.BeachAccess },
        EventIcon("pets", "Pets") { Icons.Rounded.Pets },
        EventIcon("food", "Food") { Icons.Rounded.Restaurant },
        EventIcon("coffee", "Coffee") { Icons.Rounded.LocalCafe },
        EventIcon("health", "Health") { Icons.Rounded.LocalHospital },
        EventIcon("book", "Book") { Icons.Rounded.MenuBook },
        EventIcon("money", "Money") { Icons.Rounded.Paid },
        EventIcon("sun", "Sun") { Icons.Rounded.WbSunny },
        EventIcon("moon", "Moon") { Icons.Rounded.NightsStay },
        EventIcon("bell", "Bell") { Icons.Rounded.Notifications },
        EventIcon("flag", "Flag") { Icons.Rounded.Flag },
        EventIcon("trophy", "Trophy") { Icons.Rounded.EmojiEvents },
        EventIcon("party", "Party") { Icons.Rounded.Celebration },
        EventIcon("baby", "Baby") { Icons.Rounded.ChildCare },
        EventIcon("bike", "Bike") { Icons.Rounded.DirectionsBike },
        EventIcon("football", "Sport") { Icons.Rounded.SportsSoccer },
        EventIcon("flower", "Flower") { Icons.Rounded.LocalFlorist },
        EventIcon("luggage", "Trip") { Icons.Rounded.Luggage },
        EventIcon("rocket", "Launch") { Icons.Rounded.RocketLaunch },
        EventIcon("shopping", "Shopping") { Icons.Rounded.ShoppingBag },
        EventIcon("fastfood", "Snack") { Icons.Rounded.Fastfood },
        EventIcon("palette", "Art") { Icons.Rounded.Palette },
        EventIcon("timer", "Timer") { Icons.Rounded.Timer },
        EventIcon("alarm", "Alarm") { Icons.Rounded.Alarm },
        EventIcon("building", "Office") { Icons.Rounded.Apartment },
        EventIcon("shield", "Shield") { Icons.Rounded.Shield },
    )

    private val byId = all.associateBy { it.id }
    private val paths = ConcurrentHashMap<String, Path>()

    fun get(id: String): EventIcon = byId[id] ?: byId.getValue(DEFAULT_ID)

    /** The icon as android.graphics paths on a 24x24 grid (all sub-paths merged, fill rule respected per sub-path). */
    private fun pathOf(id: String): Path = paths.getOrPut(get(id).id) {
        val out = Path()
        var evenOdd = false
        fun walk(g: VectorGroup) {
            for (node in g) when (node) {
                is VectorPath -> {
                    val p = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                    if (node.pathFillType == PathFillType.EvenOdd) evenOdd = true
                    out.addPath(p)
                }
                is VectorGroup -> walk(node)
            }
        }
        walk(get(id).vector().root)
        if (evenOdd) out.fillType = Path.FillType.EVEN_ODD
        out
    }

    private val scratch = ThreadLocal.withInitial { Paint(Paint.ANTI_ALIAS_FLAG) }

    /** Draws icon [id] filling [bounds] (kept square, centred) in [color]. */
    fun draw(canvas: Canvas, id: String, bounds: RectF, color: Int) {
        val size = minOf(bounds.width(), bounds.height())
        val paint = scratch.get()!!
        paint.style = Paint.Style.FILL
        paint.color = color
        val save = canvas.save()
        canvas.translate(bounds.centerX() - size / 2f, bounds.centerY() - size / 2f)
        canvas.scale(size / 24f, size / 24f)
        canvas.drawPath(pathOf(id), paint)
        canvas.restoreToCount(save)
    }
}
