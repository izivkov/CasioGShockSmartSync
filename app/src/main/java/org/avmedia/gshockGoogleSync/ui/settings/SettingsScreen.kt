package org.avmedia.gshockGoogleSync.ui.settings

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.InfoButton
import org.avmedia.gshockGoogleSync.ui.common.ItemList
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.translateapi.DynamicResourceApi

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SettingsScreen() {
    GShockSmartSyncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ConstraintLayout(
                modifier = Modifier.fillMaxSize()
            ) {
                val (title, settingsLayout, buttonsRow) = createRefs()

                ScreenTitle(
                    DynamicResourceApi.getApi().stringResource(context = LocalContext.current, id = R.string.settings), Modifier
                    .constrainAs(title) {
                        top.linkTo(parent.top)  // Link top of content to parent top
                        bottom.linkTo(settingsLayout.top)  // Link bottom of content to top of buttonsRow
                    })

                Column(
                    modifier = Modifier
                        .constrainAs(settingsLayout) {
                            top.linkTo(title.bottom)
                            bottom.linkTo(buttonsRow.top)
                            height = Dimension.fillToConstraints
                        }
                        .verticalScroll(rememberScrollState())  // Make content scrollable
                        .padding(0.dp)
                        .fillMaxWidth()
                        .fillMaxSize()
                ) {
                    SettingsList()
                }

                BottomRow(modifier = Modifier.constrainAs(buttonsRow) {
                    top.linkTo(settingsLayout.bottom)  // Link top of buttonsRow to bottom of content
                    bottom.linkTo(parent.bottom)  // Keep buttons at the bottom
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun SettingsList() {

    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val settingsViews = arrayListOf(
        Locale(settingsViewModel::updateSetting),
        OperationalTone(settingsViewModel::updateSetting),
        Light(settingsViewModel::updateSetting)
    ).apply {
        if (WatchInfo.hasPowerSavingMode) add(PowerSavings(settingsViewModel::updateSetting))
        add(TimeAdjustment(settingsViewModel::updateSetting))
    }

    Column(
        modifier = Modifier
    ) {
        ItemList(settingsViews)
    }
}

@Composable
fun BottomRow(
    modifier: Modifier,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,  // Center vertically
            horizontalArrangement = Arrangement.SpaceEvenly,  // Arrange horizontally, starting from the left
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(end = 0.dp),
                contentAlignment = Alignment.CenterEnd  // Aligns content to the right
            ) {
                InfoButton(infoText = DynamicResourceApi.getApi().stringResource(context = LocalContext.current, id = R.string.auto_fill_help))
            }

            val buttons = arrayListOf(
                ButtonData(
                    text = Utils.shortenString(
                        DynamicResourceApi.getApi().stringResource(context = LocalContext.current, id = R.string.auto_configure_settings),
                        15, "AUTO"),
                    onClick = { settingsViewModel.setSmartDefaults() }),

                ButtonData(
                    text = DynamicResourceApi.getApi().stringResource(context = LocalContext.current, id = R.string.send_to_watch),
                    onClick = { settingsViewModel.sendToWatch() })
            )

            ButtonsRow(buttons = buttons, modifier = Modifier.weight(2.5f))

            Spacer(modifier = Modifier.weight(.5f))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSettingsScreen() {
    SettingsScreen()
}
