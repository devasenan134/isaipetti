package io.github.devasenan134.isaipetti.ui.theme

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

private val Gold = Color(0xFFF5B942)
private val DeepGold = Color(0xFF7A5200)

private val DarkColors = darkColorScheme(primary = Gold, onPrimary = Color(0xFF3F2B00))
private val LightColors = lightColorScheme(primary = DeepGold)

/** Uses the phone's wallpaper colours on Android 12+ (Material You), gold everywhere else. */
@Composable
fun IsaipettiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
