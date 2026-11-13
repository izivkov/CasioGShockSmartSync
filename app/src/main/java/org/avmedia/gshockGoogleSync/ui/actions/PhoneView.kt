package org.avmedia.gshockGoogleSync.ui.actions
import androidx.hilt.navigation.compose.hiltViewModel

import AppTextLarge
import AppTextLink
import AppSwitch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.avmedia.gshockGoogleSync.ActionContainer
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppIconFromResource
import org.avmedia.gshockGoogleSync.ui.common.AppPhoneInputDialog

@Composable
fun PhoneView(actionsViewModel: ActionsViewModel = hiltViewModel()) {
    val defaultPhone = "000-000-0000"

    // Seeded from the session's draft, so edits survive switching screens.
    var isEnabled by remember {
        mutableStateOf(actionsViewModel.isEnabled(ActionContainer.PhoneDialAction::class.java))
    }
    var phoneNumber by remember {
        mutableStateOf(
            actionsViewModel.phoneNumber().takeIf { it.isNotBlank() }?.trim() ?: defaultPhone
        )
    }
    var showDialog by remember { mutableStateOf(false) }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconFromResource(resourceId = R.drawable.phone, contentDescription = "")

            Column(modifier = Modifier.weight(1f).padding(6.dp)) {
                AppTextLarge(text = stringResource(id = R.string.make_phonecall))

                AppTextLink(
                    text = phoneNumber,
                    modifier = Modifier.clickable { showDialog = true },
                    textAlign = TextAlign.Start,
                    fontSize = 20.sp
                )

                if (showDialog) {
                    AppPhoneInputDialog(
                        initialPhoneNumber = phoneNumber,
                        onDismiss = { showDialog = false },
                        onPhoneNumberValidated = { newValue ->
                            val newPhone = newValue.ifEmpty { defaultPhone }
                            phoneNumber = newPhone
                            showDialog = false
                            actionsViewModel.setPhoneNumber(newPhone)
                        }
                    )
                }
            }

            AppSwitch(
                checked = isEnabled,
                onCheckedChange = { newValue: Boolean ->
                    isEnabled = newValue
                    actionsViewModel.setEnabled(ActionContainer.PhoneDialAction::class.java, newValue)
                }
            )
        }
    }
}
