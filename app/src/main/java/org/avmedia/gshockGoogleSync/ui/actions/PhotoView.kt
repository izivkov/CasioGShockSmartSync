import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppIconFromResource
import org.avmedia.gshockGoogleSync.utils.Utils

@Composable
fun PhotoView(
    onUpdate: (ActionsViewModel.PhotoAction) -> Unit,
    actionsViewModel: ActionsViewModel = hiltViewModel()
) {
    data class ViewState(
        val isEnabled: Boolean,
        val cameraOrientation: ActionsViewModel.CameraOrientation,
        val action: ActionsViewModel.PhotoAction
    )

    val actions by actionsViewModel.actions.collectAsState()
    val photoAction = remember {
        actionsViewModel.getAction(ActionsViewModel.PhotoAction::class.java)
    }

    var viewState by remember {
        mutableStateOf(
            ViewState(
                isEnabled = photoAction.enabled,
                cameraOrientation = photoAction.cameraOrientation,
                action = photoAction
            )
        )
    }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.padding(4.dp)) {
                AppIconFromResource(resourceId = R.drawable.camera)
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextLarge(
                    text = Utils.shortenString(
                        stringResource(id = R.string.take_photo),
                        15
                    ),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    CameraOrientationOption(
                        text = stringResource(id = R.string.front_cam),
                        isSelected = viewState.cameraOrientation == ActionsViewModel.CameraOrientation.FRONT,
                        onSelect = {
                            val newState = viewState.copy(
                                cameraOrientation = ActionsViewModel.CameraOrientation.FRONT
                            )
                            viewState = newState
                            onUpdate(viewState.action.copy(cameraOrientation = newState.cameraOrientation))
                        }
                    )

                    CameraOrientationOption(
                        text = stringResource(id = R.string.back_cam),
                        isSelected = viewState.cameraOrientation == ActionsViewModel.CameraOrientation.BACK,
                        onSelect = {
                            val newState = viewState.copy(
                                cameraOrientation = ActionsViewModel.CameraOrientation.BACK
                            )
                            viewState = newState
                            onUpdate(viewState.action.copy(cameraOrientation = newState.cameraOrientation))
                        }
                    )
                }
            }

            AppSwitch(
                checked = viewState.isEnabled,
                onCheckedChange = { newValue ->
                    val newState = viewState.copy(isEnabled = newValue)
                    viewState = newState
                    onUpdate(viewState.action.copy(enabled = newValue))
                },
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun CameraOrientationOption(
    text: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            modifier = Modifier
        )
        Text(text = text)
    }
}
