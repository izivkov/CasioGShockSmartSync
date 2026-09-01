import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.telephony.TelephonyManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat.getSystemService
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    val state by timeModel.state.collectAsState()
    WatchTemperatureContent(
        hasTemperature = hasTemperature,
        isConnected = isConnected,
        isNormalButtonPressed = isNormalButtonPressed,
        temperature = state.temperature
    )
}

@Composable
fun WatchTemperatureContent(
    hasTemperature: Boolean,
    isConnected: Boolean,
    isNormalButtonPressed: Boolean,
    temperature: Int
) {
    var temperatureText by remember { mutableStateOf("") }
    val context = LocalContext.current

    val isHighTemp = temperature >= 40
    val infiniteTransition = rememberInfiniteTransition(label = "temp flash")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isHighTemp) 0.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "temp alpha"
    )

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
            temperatureText = ""
        }
    }

    AppText(
        text = temperatureText,
        modifier = Modifier.alpha(if (isHighTemp) alpha else 1f)
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewWatchTemperatureNormal() {
    WatchTemperatureContent(
        hasTemperature = true,
        isConnected = true,
        isNormalButtonPressed = true,
        temperature = 25
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewWatchTemperatureHigh() {
    WatchTemperatureContent(
        hasTemperature = true,
        isConnected = true,
        isNormalButtonPressed = true,
        temperature = 42
    )
}
