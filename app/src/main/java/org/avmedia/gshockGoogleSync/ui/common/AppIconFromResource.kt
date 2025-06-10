package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R

@Composable
fun AppIconFromResource(
    modifier: Modifier = Modifier.size(28.dp),
    resourceId: Int = R.drawable.prayer_times,
    contentDescription: String = "",
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 28.dp
) {
    val drawablePainter = painterResource(id = resourceId)

    Icon(
        painter = drawablePainter,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size)
    )
}
