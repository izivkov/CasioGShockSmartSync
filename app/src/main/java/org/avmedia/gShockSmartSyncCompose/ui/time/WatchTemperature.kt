import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockSmartSyncCompose.ui.time.TimeViewModel
import java.util.Locale

@Composable
fun WatchTemperature(
    modifier: Modifier = Modifier,
    hasTemperature: Boolean,
    isConnected: Boolean,
    isNormalButtonPressed: Boolean,
    timeModel: TimeViewModel = viewModel()
) {
    var temperatureText by remember { mutableStateOf("N/A") }
    val temperature by timeModel.temperature.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(temperature) {
        if (hasTemperature && isConnected && isNormalButtonPressed) {
            launch(Dispatchers.IO) {

                val tm = getSystemService(context, TelephonyManager::class.java)
                val countryCodeValue = tm?.networkCountryIso ?: ""
                val isUS = (countryCodeValue.isNotEmpty() && countryCodeValue.uppercase() == "US")
                val fmt =
                    MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
                val measure = if (isUS) {
                    Measure(
                        ((temperature * 9 / 5) + 32),
                        MeasureUnit.FAHRENHEIT
                    )
                } else {
                    Measure(temperature, MeasureUnit.CELSIUS)
                }

                launch(Dispatchers.Main) {
                    temperatureText = fmt.format(measure)
                }
            }
        } else if (!hasTemperature) {
            temperatureText = "N/A"
        }
    }

    AppText(
        text = temperatureText,
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewWatchTemperature() {
    // Provide the required parameters for preview
    WatchTemperature(
        hasTemperature = true,
        isConnected = true,
        isNormalButtonPressed = true,
    )
}

