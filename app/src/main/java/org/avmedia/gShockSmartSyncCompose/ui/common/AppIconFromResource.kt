package org.avmedia.gShockSmartSyncCompose.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.avmedia.gShockSmartSyncCompose.R

@Composable
fun AppIconFromResource(
    resourceId: Int = R.drawable.prayer_times,
    contentDescription: String = ""
) {
    val drawablePainter = painterResource(id = resourceId)

    Icon(
        painter = drawablePainter,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(28.dp)
    )
}
