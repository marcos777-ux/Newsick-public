package com.makro17.newsick.ui.theme

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
private val DarkColorScheme = darkColorScheme(
    primary = ElectricLime,
    onPrimary = BackgroundBlack,
    secondary = ElectricPurple,
    onSecondary = TextWhite,
    tertiary = CustomGold,
    background = BackgroundBlack,
    surface = SurfaceDark,
    onBackground = TextWhite,
    onSurface = TextWhite,
    error = CustomAlert
)

private val LightColorScheme = lightColorScheme(
    primary = DarkLime,
    secondary = DeepPurple,
    tertiary = CustomGold,
    background = TextWhite,
    surface = Color(0xFFF5F5F5),
    onPrimary = Color.White,
    onSecondary = Color.White
)

@Composable
fun NewsickTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}