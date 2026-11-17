package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BottomTab(val route: String, val label: String, val icon: ImageVector)

/**
 * Bottom bar with a pill highlight that glides between tabs on a spring (stretching slightly
 * while it travels), a small icon bounce, animated colours and a label that fades / slides in.
 */
@Composable
fun AnimatedBottomBar(tabs: List<BottomTab>, selectedRoute: String?, onSelect: (BottomTab) -> Unit) {
    val selected = tabs.indexOfFirst { it.route == selectedRoute }
    val cs = MaterialTheme.colorScheme
    val glide = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

    // Remember where the pill came from to stretch it towards the direction of travel.
    var lastIndex by remember { mutableIntStateOf(selected.coerceAtLeast(0)) }

    Surface(color = cs.surfaceContainer, tonalElevation = 3.dp) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(72.dp),
        ) {
            val itemWidth = maxWidth / tabs.size.coerceAtLeast(1)
            val pillWidth = 64.dp
            val target = itemWidth * selected.coerceAtLeast(0) + (itemWidth - pillWidth) / 2
            val pillX by animateDpAsState(target, glide, label = "pillX", finishedListener = { lastIndex = selected })
            val travelling = selected != lastIndex
            val stretch by animateFloatAsState(
                if (travelling) 1.35f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "pillStretch",
            )
            val pillAlpha by animateFloatAsState(if (selected >= 0) 1f else 0f, tween(200), label = "pillAlpha")

            // The gliding highlight
            Box(
                modifier = Modifier
                    .offset(x = pillX, y = 12.dp)
                    .width(pillWidth)
                    .height(32.dp)
                    .graphicsLayer { scaleX = stretch; alpha = pillAlpha }
                    .background(cs.secondaryContainer, RoundedCornerShape(16.dp)),
            )

            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                tabs.forEachIndexed { i, tab ->
                    val isSel = i == selected
                    val tint by animateColorAsState(
                        if (isSel) cs.onSecondaryContainer else cs.onSurfaceVariant, tween(250), label = "tabTint",
                    )
                    val iconScale by animateFloatAsState(
                        if (isSel) 1.12f else 1f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "tabIconScale",
                    )
                    Column(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null, // the pill is the feedback
                            ) { if (!isSel) onSelect(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(modifier = Modifier.padding(top = 16.dp).height(24.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                            )
                        }
                        AnimatedVisibility(
                            visible = isSel,
                            enter = fadeIn(tween(220, delayMillis = 60)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
                        ) {
                            Text(
                                tab.label,
                                color = tint,
                                fontSize = 12.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
