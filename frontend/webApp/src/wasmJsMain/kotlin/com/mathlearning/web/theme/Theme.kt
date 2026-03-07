package com.mathlearning.web.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import math_learning_frontend.webapp.generated.resources.Res
import math_learning_frontend.webapp.generated.resources.noto_sans_sc

@Composable
fun getAppTypography(): Typography {
    val NotoSansSC = FontFamily(
        Font(Res.font.noto_sans_sc)
    )
    
    return Typography(
        displayLarge = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
        displayMedium = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
        headlineLarge = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        titleSmall = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
    )
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFF7C5800),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF271900),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FAFE),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = getAppTypography(),
        content = content,
    )
}
