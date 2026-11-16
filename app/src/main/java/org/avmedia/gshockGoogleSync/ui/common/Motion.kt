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
    private const val TAB_MS = 260

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
        return slideInHorizontally(tween(TAB_MS)) { full -> d * full / 6 } + fadeIn(tween(TAB_MS))
    }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(): ExitTransition {
        val d = direction()
        return slideOutHorizontally(tween(TAB_MS)) { full -> -d * full / 6 } + fadeOut(tween(TAB_MS - 60))
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
