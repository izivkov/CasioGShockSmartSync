package org.avmedia.gShockSmartSyncCompose.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.avmedia.gShockSmartSyncCompose.R

@Composable
fun AppIcon(
    imageVector: ImageVector = ImageVector.vectorResource(id = R.drawable.ic_actions), // Local icon
    contentDescription: String = ""
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(28.dp)
    )
}
