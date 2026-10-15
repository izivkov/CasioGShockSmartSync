import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/** [AppTextLarge] at 24sp, inheriting the surrounding text color. */
@Composable
fun AppTextVeryLarge(text: String, modifier: Modifier = Modifier) =
    AppTextLarge(
        text = text,
        modifier = modifier,
        textStyle = TextStyle(fontSize = 24.sp, color = Color.Unspecified)
    )
