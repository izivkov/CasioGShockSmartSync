package org.avmedia.gshockGoogleSync.ui.actions

import AppSwitch
import AppTextLarge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppIconFromResource
import org.avmedia.gshockGoogleSync.ui.common.HorizontalSeparator
import org.avmedia.gshockGoogleSync.ui.common.InfoButton
import org.avmedia.gshockGoogleSync.webhook.ServiceToggles
import org.avmedia.gshockGoogleSync.webhook.WebhookStorage

@Composable
fun ActionItem(
    modifier: Modifier = Modifier,
    title: String,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    infoText: String? = null,
    resourceId: Int = R.drawable.generic_action_item,
    /** When set, a small configure button sits next to the switch. */
    onConfigure: (() -> Unit)? = null,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                onConfigure?.let {
                    IconButton(onClick = it) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(id = R.string.settings),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                AppSwitch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange
                )
            }
        }
    }
}

/** An [ActionItem] for a service master switch: saved at once, with a configure button that opens its screen. */
@Composable
fun ServiceToggleRow(service: ServiceToggles.Service, titleRes: Int, iconRes: Int, onConfigure: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val toggles = remember { WebhookStorage.toggles(context) }
    var isOn by remember { mutableStateOf(toggles.isOn(service)) }
    ActionItem(
        title = stringResource(id = titleRes),
        resourceId = iconRes,
        isEnabled = isOn,
        onEnabledChange = { isOn = it; toggles.set(service, it) },
        onConfigure = onConfigure,
    )
}

/**
 * An [ActionItem] bound to the enabled flag of the action registered under [actionClass].
 * Toggling the switch only records a pending change; it is applied by [ActionsViewModel.save].
 */
@Composable
fun <T : ActionContainer.Action> ToggleActionView(
    actionClass: Class<T>,
    titleRes: Int,
    iconRes: Int,
    actionsViewModel: ActionsViewModel,
    infoRes: Int? = null,
) {
    var isEnabled by remember { mutableStateOf(actionsViewModel.isEnabled(actionClass)) }

    ActionItem(
        title = stringResource(id = titleRes),
        resourceId = iconRes,
        infoText = infoRes?.let { stringResource(id = it) },
        isEnabled = isEnabled,
        onEnabledChange = { newValue ->
            isEnabled = newValue
            actionsViewModel.setEnabled(actionClass, newValue)
        }
    )
}
