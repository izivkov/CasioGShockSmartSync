package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavBackStackEntry
import org.avmedia.gshockGoogleSync.Screens

/** Shared motion: press "squish" for buttons and directional tab transitions. */
object Motion {
    // Material 3 "emphasized" curves: decelerating enter, quick accelerating exit.
    private const val ENTER_MS = 380
    private const val EXIT_MS = 180
    private val EMPHASIZED_DECELERATE = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EMPHASIZED_ACCELERATE = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    private val TAB_ORDER = listOf(
        Screens.Time, Screens.Health, Screens.Alarms, Screens.Events, Screens.Actions, Screens.Custom, Screens.Settings,
    ).map { it.route }

    /** +1 when moving to a tab on the right, -1 to the left. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.direction(): Int {
        val from = TAB_ORDER.indexOf(initialState.destination.route)
        val to = TAB_ORDER.indexOf(targetState.destination.route)
        return if (to >= from) 1 else -1
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(): EnterTransition {
        val d = direction()
        return slideInHorizontally(tween(ENTER_MS, easing = EMPHASIZED_DECELERATE)) { full -> d * full / 8 } +
            fadeIn(tween(ENTER_MS - 80, delayMillis = 40, easing = EMPHASIZED_DECELERATE))
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(): ExitTransition {
        val d = direction()
        return slideOutHorizontally(tween(EXIT_MS, easing = EMPHASIZED_ACCELERATE)) { full -> -d * full / 12 } +
            fadeOut(tween(EXIT_MS, easing = EMPHASIZED_ACCELERATE))
    }
}

/** Interaction source + a modifier that scales the component down slightly while pressed. */
@Composable
fun rememberPressScale(): Pair<MutableInteractionSource, Modifier> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return source to Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
}
