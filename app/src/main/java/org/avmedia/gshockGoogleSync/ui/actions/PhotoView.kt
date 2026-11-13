package org.avmedia.gshockGoogleSync.ui.actions
import androidx.hilt.navigation.compose.hiltViewModel

import AppTextLarge
import AppSwitch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import org.avmedia.gshockGoogleSync.utils.Utils

@Composable
fun PhotoView(actionsViewModel: ActionsViewModel = hiltViewModel()) {
    // Seeded from the session's draft, so edits survive switching screens.
    var isEnabled by remember {
        mutableStateOf(actionsViewModel.isEnabled(ActionContainer.PhotoAction::class.java))
    }
    var cameraOrientation by remember { mutableStateOf(actionsViewModel.cameraOrientation()) }

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
                        isSelected = cameraOrientation == ActionContainer.CameraOrientation.FRONT,
                        onSelect = {
                            cameraOrientation = ActionContainer.CameraOrientation.FRONT
                            actionsViewModel.setCameraOrientation(cameraOrientation)
                        }
                    )

                    CameraOrientationOption(
                        text = stringResource(id = R.string.back_cam),
                        isSelected = cameraOrientation == ActionContainer.CameraOrientation.BACK,
                        onSelect = {
                            cameraOrientation = ActionContainer.CameraOrientation.BACK
                            actionsViewModel.setCameraOrientation(cameraOrientation)
                        }
                    )
                }
            }

            AppSwitch(
                checked = isEnabled,
                onCheckedChange = { newValue: Boolean ->
                    isEnabled = newValue
                    actionsViewModel.setEnabled(ActionContainer.PhotoAction::class.java, newValue)
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
