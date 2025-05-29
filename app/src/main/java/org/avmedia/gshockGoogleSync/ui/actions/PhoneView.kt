import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppIconFromResource
import org.avmedia.gshockGoogleSync.ui.common.AppPhoneInputDialog

@Composable
fun PhoneView(
    onUpdate: (ActionsViewModel.PhoneDialAction) -> Unit,
    actionsViewModel: ActionsViewModel = hiltViewModel(),
) {
    val classType = ActionsViewModel.PhoneDialAction::class.java
    val actions by actionsViewModel.actions.collectAsState()
    val phoneDialAction: ActionsViewModel.PhoneDialAction =
        actionsViewModel.getAction(classType)

    var isEnabled by remember { mutableStateOf(phoneDialAction.enabled) }
    val defaultPhone = "000-000-0000"
    var phoneNumber by remember {
        mutableStateOf(
            phoneDialAction.phoneNumber.takeIf { it.isNotBlank() }?.trim() ?: defaultPhone
        )
    }

    LaunchedEffect(actions, phoneDialAction) {
        isEnabled = phoneDialAction.enabled
        phoneNumber = phoneDialAction.phoneNumber.takeIf { it.isNotBlank() }?.trim() ?: defaultPhone
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            AppIconFromResource(
                resourceId = R.drawable.phone,
                contentDescription = ""
            )

            // Title and EditText for Phone Number
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(6.dp)
            ) {
                AppTextLarge(
                    text = stringResource(
                        id = R.string.make_phonecall
                    ),
                )
                var showDialog by remember { mutableStateOf(false) }

                AppTextLink(
                    text = phoneNumber,
                    modifier = Modifier
                        .clickable { showDialog = true },
                    textAlign = TextAlign.Start,
                    fontSize = 20.sp
                )

                if (showDialog) {
                    AppPhoneInputDialog(
                        initialPhoneNumber = phoneNumber,
                        onDismiss = { showDialog = false },
                        onPhoneNumberValidated = { newValue ->
                            phoneNumber = newValue.ifEmpty { defaultPhone }
                            showDialog = false
                            onUpdate(phoneDialAction.copy(phoneNumber = phoneNumber))
                        },
                    )
                }
            }

            AppSwitch(
                checked = isEnabled,
                onCheckedChange = { newValue ->
                    isEnabled = newValue // Update the state when the switch is toggled
                    phoneDialAction.enabled = newValue
                    onUpdate(phoneDialAction.copy(enabled = newValue))
                },
            )
        }
    }
}


