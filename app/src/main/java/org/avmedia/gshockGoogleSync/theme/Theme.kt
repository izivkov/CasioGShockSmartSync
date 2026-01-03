package org.avmedia.gshockGoogleSync.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Expressive light and dark color schemes
private val LightColorScheme =
        lightColorScheme(
                primary = PrimaryLight,
                onPrimary = OnPrimaryLight,
                primaryContainer = PrimaryContainerLight,
                onPrimaryContainer = OnPrimaryContainerLight,
                secondary = SecondaryLight,
                onSecondary = OnSecondaryLight,
                secondaryContainer = SecondaryContainerLight,
                onSecondaryContainer = OnSecondaryContainerLight,
                tertiary = TertiaryLight,
                onTertiary = OnTertiaryLight,
                tertiaryContainer = TertiaryContainerLight,
                onTertiaryContainer = OnTertiaryContainerLight,
                error = ErrorLight,
                onError = OnErrorLight,
                errorContainer = ErrorContainerLight,
                onErrorContainer = OnErrorContainerLight,
                background = BackgroundLight,
                onBackground = OnBackgroundLight,
                surface = SurfaceLight,
                onSurface = OnSurfaceLight,
                surfaceVariant = SurfaceVariantLight,
                onSurfaceVariant = OnSurfaceVariantLight,
                outline = OutlineLight,
        )

private val DarkColorScheme =
        darkColorScheme(
                primary = PrimaryDark,
                onPrimary = OnPrimaryDark,
                primaryContainer = PrimaryContainerDark,
                onPrimaryContainer = OnPrimaryContainerDark,
                secondary = SecondaryDark,
                onSecondary = OnSecondaryDark,
                secondaryContainer = SecondaryContainerDark,
                onSecondaryContainer = OnSecondaryContainerDark,
                tertiary = TertiaryDark,
                onTertiary = OnTertiaryDark,
                tertiaryContainer = TertiaryContainerDark,
                onTertiaryContainer = OnTertiaryContainerDark,
                error = ErrorDark,
                onError = OnErrorDark,
                errorContainer = ErrorContainerDark,
                onErrorContainer = OnErrorContainerDark,
                background = BackgroundDark,
                onBackground = OnBackgroundDark,
                surface = SurfaceDark,
                onSurface = OnSurfaceDark,
                surfaceVariant = SurfaceVariantDark,
                onSurfaceVariant = OnSurfaceVariantDark,
                outline = OutlineDark,
        )

// Composable function to provide the color scheme based on Android version and theme
@Composable
fun getCurrentColorScheme(darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current

    return if (darkTheme) DarkColorScheme else LightColorScheme
}

@Composable
fun GShockSmartSyncTheme(
        darkTheme: Boolean = isSystemInDarkTheme(),
        // Dynamic color is available on Android 12+
        dynamicColor: Boolean = true,
        content: @Composable () -> Unit
) {
    val colorScheme =
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context)
                    else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            val window = activity?.window

            if (window != null) {
                // Configure light or dark appearance for the status bar icons
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
            colorScheme = colorScheme,
            shapes = Shapes,
            typography = Typography,
            content = content
    )
}
