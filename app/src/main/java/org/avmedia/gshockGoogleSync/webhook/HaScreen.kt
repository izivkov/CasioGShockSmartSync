package org.avmedia.gshockGoogleSync.webhook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import AppText
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

/** Registers the watch as a Home Assistant device (mobile_app API) so its values appear as sensors. */
@Composable
fun HaScreen(viewModel: HaViewModel, onBack: () -> Unit) {
    val form by viewModel.form.collectAsState()
    val status by viewModel.status.collectAsState()
    HaScreenContent(
        form = form,
        status = status,
        onBack = onBack,
        onForget = viewModel::forget,
        onSave = viewModel::save,
    )
}

/** The stateless body of [HaScreen]: state in, events out (also used by the screenshot tests). */
@Composable
internal fun HaScreenContent(
    form: HaConfig,
    status: String,
    onBack: () -> Unit,
    onForget: () -> Unit,
    onSave: (url: String, token: String, deviceName: String, model: String) -> Unit,
) {
    var url by remember { mutableStateOf(form.baseUrl) }
    var token by remember { mutableStateOf(form.token) }
    var deviceName by remember { mutableStateOf(form.deviceName) }
    var model by remember { mutableStateOf(form.model) }
    val saved = form.baseUrl.isNotBlank() && form.token.isNotBlank()

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(text = "Home Assistant", modifier = Modifier)
        Column(
            Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppText(
                text = "Set up once; each watch you connect becomes its own device with sensors (battery, steps, heart rate and more). " +
                    "The token is a long-lived access token from your Home Assistant profile.",
                fontSize = 14.sp,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { androidx.compose.material3.Text("Base URL, for example https://ha.example.com") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { androidx.compose.material3.Text("Long-lived access token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { androidx.compose.material3.Text("Device name (empty: the watch name, e.g. CASIO GBX-H5600)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { androidx.compose.material3.Text("Model (empty: the watch name, e.g. GBX-H5600; you can type GBX-H5600-1)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AppText(text = if (saved) "Saved: each watch registers itself when it connects" else "Not set up", fontSize = 14.sp)
            if (status.isNotBlank()) AppText(text = status, fontSize = 14.sp)
        }
        ButtonsRow(
            buttons = listOf(
                ButtonData("Back", onBack),
                ButtonData("Forget", { onForget(); url = ""; token = ""; deviceName = ""; model = "" }, enabled = saved),
                ButtonData("Save", { onSave(url, token, deviceName, model) }, enabled = url.isNotBlank() && token.isNotBlank()),
            )
        )
    }
}
