import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    data class ViewState(
        val isEnabled: Boolean,
        val phoneNumber: String,
        val showDialog: Boolean,
        val action: ActionsViewModel.PhoneDialAction
    )

    val actions by actionsViewModel.actions.collectAsState()
    val phoneDialAction = remember {
        actionsViewModel.getAction(ActionsViewModel.PhoneDialAction::class.java)
    }

    val defaultPhone = "000-000-0000"

    var viewState by remember(actions, phoneDialAction) {
        mutableStateOf(
            ViewState(
                isEnabled = phoneDialAction.enabled,
                phoneNumber = phoneDialAction.phoneNumber.takeIf { it.isNotBlank() }?.trim()
                    ?: defaultPhone,
                showDialog = false,
                action = phoneDialAction
            )
        )
    }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconFromResource(
                resourceId = R.drawable.phone,
                contentDescription = ""
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(6.dp)
            ) {
                AppTextLarge(
                    text = stringResource(id = R.string.make_phonecall)
                )

                AppTextLink(
                    text = viewState.phoneNumber,
                    modifier = Modifier.clickable {
                        viewState = viewState.copy(showDialog = true)
                    },
                    textAlign = TextAlign.Start,
                    fontSize = 20.sp
                )

                if (viewState.showDialog) {
                    AppPhoneInputDialog(
                        initialPhoneNumber = viewState.phoneNumber,
                        onDismiss = {
                            viewState = viewState.copy(showDialog = false)
                        },
                        onPhoneNumberValidated = { newValue ->
                            val newPhone = newValue.ifEmpty { defaultPhone }
                            viewState = viewState.copy(
                                phoneNumber = newPhone,
                                showDialog = false
                            )
                            onUpdate(viewState.action.copy(phoneNumber = newPhone))
                        }
                    )
                }
            }

            AppSwitch(
                checked = viewState.isEnabled,
                onCheckedChange = { newValue ->
                    viewState = viewState.copy(isEnabled = newValue)
                    viewState.action.enabled = newValue
                    onUpdate(viewState.action.copy(enabled = newValue))
                }
            )
        }
    }
}
