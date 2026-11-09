package com.focusguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta «Guardia»: un verde azulado profundo (calma y protección) con un ámbar de brasa para lo que merece atención
 * (la racha, los avisos) y el coral de siempre para el peligro. Las superficies llevan un toque del mismo verde.
 */
private val Light: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B6E6A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9EFEA),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF3F615D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE9E7),
    onSecondaryContainer = Color(0xFF0F1F1D),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2C1700),
    background = Color(0xFFF2F7F6),
    onBackground = Color(0xFF151D1C),
    surface = Color(0xFFFBFDFC),
    onSurface = Color(0xFF151D1C),
    surfaceVariant = Color(0xFFDCE8E5),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6B7573),
    outlineVariant = Color(0xFFC3CECB),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6FAF9),
    surfaceContainer = Color(0xFFEDF3F2),
    surfaceContainerHigh = Color(0xFFE7EEEC),
    surfaceContainerHighest = Color(0xFFE1E9E7),
)

private val Dark: ColorScheme = darkColorScheme(
    primary = Color(0xFF6FD6CD),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFF9DF2E9),
    secondary = Color(0xFFB1CCC7),
    onSecondary = Color(0xFF1C3532),
    secondaryContainer = Color(0xFF2B4441),
    onSecondaryContainer = Color(0xFFCCE8E3),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF663E00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF0B1413),
    onBackground = Color(0xFFDDE5E3),
    surface = Color(0xFF111B1A),
    onSurface = Color(0xFFDDE5E3),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899391),
    outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF060F0E),
    surfaceContainerLow = Color(0xFF0F1918),
    surfaceContainer = Color(0xFF141F1E),
    surfaceContainerHigh = Color(0xFF1E2928),
    surfaceContainerHighest = Color(0xFF293433),
)

/** Los dos extremos del degradado de la marca (cabecera de Inicio, bienvenida) y el texto que va encima; claro y oscuro los eligen aparte. */
class BrandColors(val start: Color, val end: Color, val onBrand: Color) {
    /** Se crea una vez con la paleta: las pantallas que se repintan cada segundo no lo reconstruyen. */
    val gradient: Brush = Brush.linearGradient(listOf(start, end))
}

private val LightBrand = BrandColors(Color(0xFF0E827C), Color(0xFF064E55), Color.White)
private val DarkBrand = BrandColors(Color(0xFF0A5C57), Color(0xFF062F33), Color(0xFFE3FFFB))

val LocalBrandColors = staticCompositionLocalOf { LightBrand }

/** Las voces de la marca: títulos con peso, para que la jerarquía no dependa solo del tamaño. */
private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Paleta propia en claro y oscuro. No usa el color dinámico del fondo de pantalla para mantener contrastes comprobados. */
@Composable
fun FocusGuardTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBrandColors provides if (darkTheme) DarkBrand else LightBrand) {
        MaterialTheme(colorScheme = if (darkTheme) Dark else Light, shapes = AppShapes, typography = AppTypography, content = content)
    }
}
