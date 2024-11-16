package org.avmedia.gShockPhoneSync.ui.actions

import AppSwitch
import AppTextLarge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.avmedia.gShockPhoneSync.ui.common.AppCard
import org.avmedia.gShockPhoneSync.ui.common.AppIconFromResource
import org.avmedia.gShockPhoneSync.ui.common.HorizontalSeparator
import org.avmedia.gShockPhoneSync.ui.common.InfoButton
import org.avmedia.gShockSmartSync.R

typealias AppIconComposable = @Composable (imageVector: ImageVector) -> Unit

@Composable
fun ActionItem(
    modifier: Modifier = Modifier,
    title: String,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    infoText: String? = null,
    resourceId: Int = R.drawable.generic_action_item
) {
    AppCard(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconFromResource(resourceId = resourceId)

                HorizontalSeparator(width = 4.dp)

                AppTextLarge(
                    text = title,
                )

                infoText?.let {
                    HorizontalSeparator(width = 4.dp)
                    InfoButton(infoText = it)
                }
            }
            AppSwitch(
                checked = isEnabled,
                onCheckedChange = { isChecked ->
                    onEnabledChange(isChecked) // Update the parent or associated state
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewActionItem() {
    ActionItem(
        title = "Action Item",
        isEnabled = true,
        onEnabledChange = {},
        infoText = null
    )
}
