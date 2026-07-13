package com.densmac.dashcam.core.design.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.densmac.dashcam.data.datastore.ThemeMode

private val DarkColors = darkColorScheme(
    primary = CameraLime,
    onPrimary = Color(0xFF241307),
    secondary = AmberRecord,
    onSecondary = Color(0xFF2A1600),
    tertiary = BurntClay,
    error = SoftRed,
    background = Graphite950,
    onBackground = WarmWhite,
    surface = NightGlass,
    onSurface = WarmWhite,
    surfaceVariant = RoadOlive,
    onSurfaceVariant = SoftGrey,
    outline = Graphite720
)

private val LightColors = lightColorScheme(
    primary = DeepCameraGreen,
    onPrimary = Color(0xFFFFF3DD),
    secondary = AmberRecord,
    onSecondary = Color(0xFF2A1600),
    tertiary = BurntClay,
    error = Color(0xFFB3261E),
    background = OffWhite,
    onBackground = Color(0xFF22180F),
    surface = Color(0xFFFFF1D8),
    onSurface = Color(0xFF22180F),
    surfaceVariant = MintPanel,
    onSurfaceVariant = Color(0xFF6B5B43),
    outline = Color(0xFFE4CBA7)
)

@Composable
fun DashcamTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context is Activity ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = DashcamTypography,
        shapes = DashcamShapes,
        content = content
    )
}
