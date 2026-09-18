package app.casz.notifybridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────
//  Color Tokens
// ─────────────────────────────────────────────
object AppColors {
    // Brand
    val Primary        = Color(0xFF007BFF)  // Azul principal (invariante)
    val PrimaryDark    = Color(0xFF0056B3)  // Azul oscuro para hover/press
    val PrimaryLight   = Color(0xFFDBEAFE)  // Azul muy claro para containers
    val PrimaryOnLight = Color(0xFF1D4ED8)  // Azul sobre fondos claros

    // Backgrounds
    val Background     = Color(0xFFF8FAFC)  // Blanco-azulado muy suave
    val Surface        = Color(0xFFFFFFFF)  // Blanco puro para cards
    val SurfaceVariant = Color(0xFFEFF6FF)  // Azul hielo para cards secundarias
    val SurfaceElevated = Color(0xFFF1F5F9) // Superficies elevadas

    // Text
    val OnBackground   = Color(0xFF0F172A)  // Casi negro
    val OnSurface      = Color(0xFF1E293B)  // Gris muy oscuro
    val OnSurfaceVar   = Color(0xFF475569)  // Gris medio
    val TextSubtle     = Color(0xFF94A3B8)  // Gris claro (hints, placeholders)
    val OnPrimary      = Color(0xFFFFFFFF)

    // Semantic — Status colors
    val StatusPending    = Color(0xFFD97706)  // Ámbar
    val StatusPendingBg  = Color(0xFFFEF3C7)
    val StatusProcessing = Color(0xFF0284C7)  // Azul claro
    val StatusProcessingBg = Color(0xFFE0F2FE)
    val StatusSuccess    = Color(0xFF16A34A)  // Verde
    val StatusSuccessBg  = Color(0xFFDCFCE7)
    val StatusFailed     = Color(0xFFDC2626)  // Rojo
    val StatusFailedBg   = Color(0xFFFEE2E2)
    val StatusCancelled  = Color(0xFF64748B)  // Gris medio
    val StatusCancelledBg = Color(0xFFF1F5F9)

    // Source badge colors
    val SourceSms  = Color(0xFFD97706)  // Ámbar
    val SourceSmsBg = Color(0xFFFEF3C7)
    val SourceApp  = Color(0xFF16A34A)  // Verde
    val SourceAppBg = Color(0xFFDCFCE7)
    val SourceImap = Color(0xFF7C3AED)  // Violeta
    val SourceImapBg = Color(0xFFEDE9FE)

    // Semantic
    val Error        = Color(0xFFDC2626)
    val ErrorBg      = Color(0xFFFEE2E2)
    val Warning      = Color(0xFFD97706)
    val WarningBg    = Color(0xFFFEF3C7)
    val Success      = Color(0xFF16A34A)
    val SuccessBg    = Color(0xFFDCFCE7)

    // Dividers & borders
    val Divider      = Color(0xFFE2E8F0)
    val Border       = Color(0xFFCBD5E1)
    val BorderFocused = Primary
}

// ─────────────────────────────────────────────
//  Typography (system fonts with custom weights)
// ─────────────────────────────────────────────
val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = AppColors.OnBackground
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = AppColors.OnBackground
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = AppColors.OnSurface
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = AppColors.OnSurface
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = AppColors.OnSurface
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = AppColors.OnSurface
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = AppColors.OnSurface
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = AppColors.OnSurfaceVar
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = AppColors.OnSurface
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = AppColors.OnSurfaceVar
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        color = AppColors.OnSurfaceVar
    )
)

// ─────────────────────────────────────────────
//  Color Scheme (Light)
// ─────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary             = AppColors.Primary,
    onPrimary           = AppColors.OnPrimary,
    primaryContainer    = AppColors.PrimaryLight,
    onPrimaryContainer  = AppColors.PrimaryDark,
    secondary           = AppColors.PrimaryDark,
    onSecondary         = AppColors.OnPrimary,
    secondaryContainer  = AppColors.SurfaceVariant,
    onSecondaryContainer = AppColors.PrimaryOnLight,
    background          = AppColors.Background,
    onBackground        = AppColors.OnBackground,
    surface             = AppColors.Surface,
    onSurface           = AppColors.OnSurface,
    surfaceVariant      = AppColors.SurfaceVariant,
    onSurfaceVariant    = AppColors.OnSurfaceVar,
    outline             = AppColors.Border,
    outlineVariant      = AppColors.Divider,
    error               = AppColors.Error,
    onError             = AppColors.OnPrimary,
    errorContainer      = AppColors.ErrorBg,
    onErrorContainer    = AppColors.Error,
)

// ─────────────────────────────────────────────
//  Main Theme Composable
// ─────────────────────────────────────────────
@Composable
fun NotifyBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = AppTypography,
        content     = content
    )
}
