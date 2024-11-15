import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.ui.actions.ActionsViewModel
import org.avmedia.gShockSmartSyncCompose.ui.common.AppCard
import org.avmedia.gShockSmartSyncCompose.ui.common.AppIconFromResource
import org.avmedia.gShockSmartSyncCompose.ui.common.AppTextField

@Composable
fun PhoneView(
    onUpdate: (ActionsViewModel.PhoneDialAction) -> Unit,
    actionsViewModel: ActionsViewModel = viewModel(),
) {
    val classType = ActionsViewModel.PhoneDialAction::class.java
    val actions by actionsViewModel.actions.collectAsState()
    val phoneDialAction: ActionsViewModel.PhoneDialAction =
        actionsViewModel.getAction(classType)

    var isEnabled by remember { mutableStateOf(phoneDialAction.enabled) }
    var phoneNumber by remember { mutableStateOf(phoneDialAction.phoneNumber) }

    LaunchedEffect(actions, phoneDialAction) {
        isEnabled = phoneDialAction.enabled
        phoneNumber = phoneDialAction.phoneNumber
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
                    text = stringResource(id = R.string.make_phonecall),
                )
                Row {
                    AppTextField(
                        value = phoneNumber,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 80.dp),
                        onValueChange = { newValue ->
                            phoneNumber = newValue
                            phoneDialAction.phoneNumber = newValue
                            onUpdate(phoneDialAction.copy(phoneNumber = newValue))
                        },
                        placeholderText = stringResource(id = R.string._416_555_6789),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        textAlign = TextAlign.Start,
                    )
                }
            }

            // Switch for Action Enable/Disable
            AppSwitch(
                checked = isEnabled,
                onCheckedChange = { newValue ->
                    isEnabled = newValue // Update the state when the switch is toggled
                    phoneDialAction.enabled = newValue
                    onUpdate(phoneDialAction.copy(enabled = newValue))
                },
                modifier = Modifier.padding(end = 0.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewPhoneCall() {
    PhoneView(onUpdate = {})
}
