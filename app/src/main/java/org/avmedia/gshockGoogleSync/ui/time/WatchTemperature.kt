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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.ui.time.TimeViewModel
import java.util.Locale

@Composable
fun WatchTemperature(
    hasTemperature: Boolean,
    isConnected: Boolean,
    isNormalButtonPressed: Boolean,
    timeModel: TimeViewModel = hiltViewModel()
) {
    var temperatureText by remember { mutableStateOf("N/A") }
    val state by timeModel.state.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(state.temperature) {
        if (hasTemperature && isConnected && isNormalButtonPressed) {
            launch(Dispatchers.IO) {
                val tm = getSystemService(context, TelephonyManager::class.java)
                val countryCodeValue = tm?.networkCountryIso ?: ""
                val isUS = (countryCodeValue.isNotEmpty() && countryCodeValue.uppercase() == "US")
                val fmt =
                    MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
                val measure = if (isUS) {
                    Measure(
                        ((state.temperature * 9 / 5) + 32),
                        MeasureUnit.FAHRENHEIT
                    )
                } else {
                    Measure(state.temperature, MeasureUnit.CELSIUS)
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

