import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    description: String? = null
) {
    val handleChange = { newValue: Boolean ->
        if (enabled) {
            onCheckedChange(newValue)
        }
    }

    Switch(
        checked = checked,
        onCheckedChange = handleChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    )
}
