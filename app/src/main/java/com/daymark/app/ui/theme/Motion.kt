package com.daymark.app.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Springs translated from Apple's (damping ratio, response) model.
 * stiffness = (2π / response)², so response 0.4s ≈ 247, 0.3s ≈ 439.
 */
object Motion {
    private fun stiffness(response: Float): Float {
        val w = (2 * Math.PI / response).toFloat()
        return w * w
    }

    /** Default UI motion: critically damped, no overshoot. */
    fun <T> standard(response: Float = 0.4f): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness(response))

    /** Only for motion that follows a physical gesture or celebratory moment. */
    fun <T> bouncy(response: Float = 0.4f): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = stiffness(response))

    /** Instant press feedback (touch-down). */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = stiffness(0.15f))

    /** Slow, calm progress fills. */
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = stiffness(0.9f))
}
