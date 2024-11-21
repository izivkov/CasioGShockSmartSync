import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RealTimeClock(modifier: Modifier = Modifier) {
    // State to hold the current time
    var currentTime by remember { mutableStateOf(getCurrentTime(getSystemTimeFormat())) }

    // LaunchedEffect to update time every second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L) // Delay for 1 second
            currentTime = getCurrentTime(getSystemTimeFormat())
        }
    }

    // Display the current time
    AppTextExtraLarge(
        text = currentTime,
        modifier = modifier
    )
}

enum class TimeFormat(val pattern: String) {
    TwelveHour("h:mm:ss a"), // 12-hour format without AM/PM
    TwentyFourHour("H:mm:ss") // 24-hour format
}

fun getCurrentTime(format: TimeFormat): String {
    val sdf = SimpleDateFormat(format.pattern, Locale.getDefault())
    return sdf.format(Date())
}

fun getSystemTimeFormat(): TimeFormat {
    // Get the short time format pattern from the system's DateFormat
    val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT) as SimpleDateFormat).toPattern()

    // Return the appropriate TimeFormat enum based on the presence of "a" in the pattern
    return if (pattern.contains("a")) {
        TimeFormat.TwelveHour // 12-hour format
    } else {
        TimeFormat.TwentyFourHour // 24-hour format
    }
}
