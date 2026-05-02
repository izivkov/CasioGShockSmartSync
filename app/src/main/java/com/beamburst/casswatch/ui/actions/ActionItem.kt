package com.beamburst.casswatch.ui.actions

import AppSwitch
import com.beamburst.casswatch.ui.common.AppTextLarge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beamburst.casswatch.R
import com.beamburst.casswatch.ui.common.AppCard
import com.beamburst.casswatch.ui.common.AppIconFromResource
import com.beamburst.casswatch.ui.common.HorizontalSeparator
import com.beamburst.casswatch.ui.common.InfoButton

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
                AppTextLarge(text = title)

                infoText?.let {
                    HorizontalSeparator(width = 4.dp)
                    InfoButton(infoText = it)
                }
            }
            AppSwitch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}
