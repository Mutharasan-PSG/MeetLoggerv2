package com.meetloggerv2.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.meetloggerv2.core.R

val PoppinsFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extra_bold, FontWeight.ExtraBold),
    Font(R.font.poppins_thin, FontWeight.Thin),
    Font(R.font.poppins_light, FontWeight.Light)
)

private val defaultTypography = Typography()
val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = PoppinsFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = PoppinsFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = PoppinsFontFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = PoppinsFontFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = PoppinsFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = PoppinsFontFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = PoppinsFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = PoppinsFontFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = PoppinsFontFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = PoppinsFontFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = PoppinsFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = PoppinsFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)
