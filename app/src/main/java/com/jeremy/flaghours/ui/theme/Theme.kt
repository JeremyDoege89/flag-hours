package com.jeremy.flaghours.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF1B3A5C)
private val Orange = Color(0xFFE8772E)

private val Light = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Orange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBC9),
    onSecondaryContainer = Color(0xFF341100),
    tertiary = Color(0xFF2E7D32),
    tertiaryContainer = Color(0xFFC8E6C9),
    onTertiaryContainer = Color(0xFF0A3D0E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA2C9FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF1B3A5C),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFFFB68F),
    onSecondary = Color(0xFF552000),
    secondaryContainer = Color(0xFF7A3A0E),
    onSecondaryContainer = Color(0xFFFFDBC9),
    tertiary = Color(0xFF81C784),
    tertiaryContainer = Color(0xFF1B5E20),
    onTertiaryContainer = Color(0xFFC8E6C9),
)

/** Slightly larger type than Material defaults: readable at arm's length in a shop. */
private val Type = Typography().let { t ->
    t.copy(
        bodyLarge = t.bodyLarge.copy(fontSize = 18.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 16.sp),
        labelLarge = t.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontSize = 18.sp),
    )
}

val BigNumber = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)

/** Status/badge colors that work in both themes. */
object StatusColors {
    val inRange = Color(0xFF2E7D32)
    val belowRange = Color(0xFFF9A825)
    val aboveRange = Color(0xFF1565C0)
}

@Composable
fun FlagHoursTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, content = content)
}
