import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    val classType = ActionsViewModel.PhotoAction::class.java
    val actions by actionsViewModel.actions.collectAsState()
    val photoAction: ActionsViewModel.PhotoAction =
        actionsViewModel.getAction(classType)

    var isEnabled by remember { mutableStateOf(photoAction.enabled) }
    var cameraOrientation by remember { mutableStateOf(photoAction.cameraOrientation) }

    LaunchedEffect(actions, photoAction) {
        isEnabled = photoAction.enabled
        cameraOrientation = photoAction.cameraOrientation
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon/ImageView equivalent
            Box(modifier = Modifier.padding(4.dp)) {
                AppIconFromResource(
                    resourceId = R.drawable.camera,
                )
            }

            // Text and Radio Buttons in a Row with weight
            Row(
                modifier = Modifier
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title TextView equivalent
                AppTextLarge(
                    text = Utils.shortenString(
                        stringResource(
                            id = R.string.take_photo
                        ), 15
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                )

                Spacer(modifier = Modifier.weight(1f)) // Spacer equivalent to View with weight

                // Radio Buttons (Front/Back Camera)
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = cameraOrientation == ActionsViewModel.CameraOrientation.FRONT,
                            onClick = {
                                photoAction.cameraOrientation =
                                    ActionsViewModel.CameraOrientation.FRONT
                                cameraOrientation = ActionsViewModel.CameraOrientation.FRONT
                                onUpdate(photoAction.copy(cameraOrientation = cameraOrientation))
                            },
                            modifier = Modifier
                        )
                        Text(
                            text = stringResource(
                                id = R.string.front_cam
                            )
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = cameraOrientation == ActionsViewModel.CameraOrientation.BACK,
                            onClick = {
                                photoAction.cameraOrientation =
                                    ActionsViewModel.CameraOrientation.BACK
                                cameraOrientation = ActionsViewModel.CameraOrientation.BACK
                                onUpdate(photoAction.copy(cameraOrientation = cameraOrientation))
                            },
                            modifier = Modifier
                        )
                        Text(
                            text = stringResource(
                                id = R.string.back_cam
                            )
                        )
                    }
                }
            }

            // SwitchMaterial equivalent
            AppSwitch(
                checked = isEnabled,
                onCheckedChange = { newValue ->
                    isEnabled = newValue // Update the state when the switch is toggled
                    photoAction.enabled = newValue
                    onUpdate(photoAction.copy(enabled = isEnabled))
                },
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewPhoto() {
    PhotoView(onUpdate = {})
}
