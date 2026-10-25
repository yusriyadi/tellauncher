package id.tellolabs.launcher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

enum class ThemeMode(val storageValue: String, val label: Int) {
    SYSTEM("system", R.string.theme_system),
    LIGHT("light", R.string.theme_light),
    NIGHT("night", R.string.theme_night),
}

private val NightColors = darkColorScheme(
    primary = Color(0xFFBDBDBD), onPrimary = Color(0xFF202020),
    primaryContainer = Color(0xFF363636), onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFFBDBDBD), onSecondary = Color(0xFF202020),
    secondaryContainer = Color(0xFF303030), onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFFBDBDBD), onTertiary = Color(0xFF202020),
    tertiaryContainer = Color(0xFF363636), onTertiaryContainer = Color(0xFFE0E0E0),
    background = Color(0xFF101010), onBackground = Color(0xFFD0D0D0),
    surface = Color(0xFF101010), onSurface = Color(0xFFD0D0D0),
    surfaceVariant = Color(0xFF242424), onSurfaceVariant = Color(0xFFA0A0A0),
    surfaceTint = Color(0xFFBDBDBD),
    surfaceDim = Color(0xFF101010), surfaceBright = Color(0xFF303030),
    surfaceContainerLowest = Color(0xFF0C0C0C), surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Color(0xFF1E1E1E), surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2B2B2B),
    outline = Color(0xFF707070), outlineVariant = Color(0xFF363636),
    inverseSurface = Color(0xFFE0E0E0), inverseOnSurface = Color(0xFF303030),
    inversePrimary = Color(0xFF505050),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF404040), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0), onPrimaryContainer = Color(0xFF262626),
    secondary = Color(0xFF505050), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E2E2), onSecondaryContainer = Color(0xFF262626),
    tertiary = Color(0xFF505050), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E0E0), onTertiaryContainer = Color(0xFF262626),
    background = Color(0xFFF5F5F5), onBackground = Color(0xFF262626),
    surface = Color(0xFFF5F5F5), onSurface = Color(0xFF262626),
    surfaceVariant = Color(0xFFE5E5E5), onSurfaceVariant = Color(0xFF606060),
    surfaceTint = Color(0xFF505050),
    surfaceDim = Color(0xFFDEDEDE), surfaceBright = Color(0xFFFAFAFA),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF0F0F0),
    surfaceContainer = Color(0xFFEBEBEB), surfaceContainerHigh = Color(0xFFE5E5E5),
    surfaceContainerHighest = Color(0xFFDDDDDD),
    outline = Color(0xFF777777), outlineVariant = Color(0xFFCECECE),
    inverseSurface = Color(0xFF303030), inverseOnSurface = Color(0xFFF0F0F0),
    inversePrimary = Color(0xFFBDBDBD),
)

@Composable
fun LauncherTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.NIGHT -> true
    }
    val colors = if (dark) NightColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context.findActivity() as? ComponentActivity)?.apply {
                // Auto retains Android's navigation contrast handling, including three-button mode.
                val bars = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
