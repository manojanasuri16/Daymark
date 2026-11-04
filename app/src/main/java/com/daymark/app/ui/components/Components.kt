package com.daymark.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daymark.app.ui.theme.LocalReducedMotion
import com.daymark.app.ui.theme.Motion

/** Whether the user enabled haptic feedback in Daymark settings. */
val LocalHapticsEnabled = compositionLocalOf { true }

/** Causal, meaningful haptics only (commit, success, toggle). */
class Haptics(private val view: android.view.View, private val enabled: Boolean) {
    fun tick() { if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun confirm() {
        if (!enabled) return
        val c = if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        view.performHapticFeedback(c)
    }
    fun heavy() { if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view, enabled) }
}

/** Shrinks on touch-down (instant feedback), springs back on release. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.press(), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A tappable card with press-scale feedback. */
@Composable
fun DaymarkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val clickMod = if (onClick != null) Modifier.pressScale(source).clickable(source, indication = null, onClick = onClick) else Modifier
    Surface(
        modifier = modifier.then(clickMod),
        shape = RoundedCornerShape(24.dp),
        color = color,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Small stat tile: icon, big value, caption. */
@Composable
fun StatTile(
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    DaymarkCard(modifier = modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).graphicsLayer { },
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = tint.copy(alpha = 0.14f), modifier = Modifier.size(32.dp)) {}
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.size(12.dp))
        AnimatedText(value, MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Text that rolls vertically when its value changes (or cross-fades under reduced motion). */
@Composable
fun AnimatedText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (reduced) fadeIn() togetherWith fadeOut()
            else (slideInVertically(Motion.standard()) { it / 2 } + fadeIn()) togetherWith
                (slideOutVertically(Motion.standard()) { -it / 2 } + fadeOut())
        },
        modifier = modifier,
        label = "animatedText",
    ) { Text(it, style = style, color = color) }
}

/** A per-digit rolling counter, so only the digits that change move. */
@Composable
fun RollingNumber(value: Long, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val digits = value.toString()
    Row(modifier, horizontalArrangement = Arrangement.Center) {
        digits.forEachIndexed { index, c ->
            // Key from the right so units stay in place when the number grows a digit.
            androidx.compose.runtime.key(digits.length - index) {
                AnimatedContent(
                    targetState = c,
                    transitionSpec = {
                        if (reduced) fadeIn() togetherWith fadeOut()
                        else (slideInVertically(Motion.bouncy(0.5f)) { it } + fadeIn()) togetherWith
                            (slideOutVertically(Motion.standard(0.5f)) { -it } + fadeOut())
                    },
                    label = "digit",
                ) { Text(it.toString(), style = style, color = color) }
            }
        }
    }
}

/** Circular progress ring with a springy fill. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.gentle(), label = "ring")
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val d = size.minDimension - stroke
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(trackColor, -90f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        if (animated > 0f) {
            drawArc(color, -90f, 360f * animated, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** A horizontal bar that grows to [fraction] with a spring. */
@Composable
fun GrowingBar(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.surfaceVariant) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.gentle(), label = "bar")
    Canvas(modifier) {
        val r = size.height / 2
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        if (animated > 0f) {
            drawRoundRect(
                color,
                size = Size(maxOf(size.height, size.width * animated), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
        }
    }
}

@Composable
fun EmptyHint(title: String, body: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))
