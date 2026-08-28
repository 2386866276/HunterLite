package com.hunterlite.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题色板 Token —— 语义化命名，移植自 OpenCodeUI
 */
data class ThemeTokens(
    val bg000: Color, val bg100: Color, val bg200: Color, val bg300: Color, val bg400: Color,
    val text100: Color, val text200: Color, val text300: Color,
    val accent: Color, val accentDeep: Color, val accentSoft: Color, val accentSecondary: Color,
    val success: Color, val successBg: Color,
    val warning: Color, val warningBg: Color,
    val danger: Color, val dangerDeep: Color, val dangerBg: Color,
    val info: Color, val infoBg: Color,
    val border100: Color, val border200: Color, val border300: Color
)

object AppThemes {

    // ============ Eucalyptus 桉树（默认）- 莫兰迪灰调 + 桉树绿 ============
    val eucalyptusLight = ThemeTokens(
        bg000 = hsl(150f, 10f, 99f), bg100 = hsl(150f, 12f, 96f), bg200 = hsl(150f, 12f, 93f),
        bg300 = hsl(150f, 10f, 89f), bg400 = hsl(150f, 10f, 85f),
        text100 = hsl(170f, 15f, 15f), text200 = hsl(170f, 10f, 40f), text300 = hsl(170f, 8f, 55f),
        accent = hsl(165f, 45f, 42f), accentDeep = hsl(165f, 40f, 35f), accentSoft = hsl(165f, 50f, 48f),
        accentSecondary = hsl(200f, 45f, 50f),
        success = hsl(140f, 40f, 40f), successBg = hsl(140f, 30f, 94f),
        warning = hsl(35f, 80f, 45f), warningBg = hsl(35f, 60f, 94f),
        danger = hsl(5f, 60f, 55f), dangerDeep = hsl(5f, 55f, 40f), dangerBg = hsl(5f, 60f, 96f),
        info = hsl(200f, 50f, 50f), infoBg = hsl(200f, 40f, 95f),
        border100 = hsl(160f, 10f, 86f), border200 = hsl(160f, 10f, 82f), border300 = hsl(160f, 10f, 75f)
    )
    val eucalyptusDark = ThemeTokens(
        bg000 = hsl(210f, 20f, 18f), bg100 = hsl(210f, 20f, 14f), bg200 = hsl(210f, 20f, 11f),
        bg300 = hsl(210f, 20f, 9f), bg400 = hsl(210f, 25f, 6f),
        text100 = hsl(210f, 15f, 92f), text200 = hsl(210f, 10f, 70f), text300 = hsl(210f, 8f, 55f),
        accent = hsl(165f, 50f, 55f), accentDeep = hsl(165f, 45f, 45f), accentSoft = hsl(165f, 55f, 65f),
        accentSecondary = hsl(200f, 50f, 60f),
        success = hsl(140f, 50f, 55f), successBg = hsl(140f, 30f, 15f),
        warning = hsl(35f, 80f, 60f), warningBg = hsl(35f, 30f, 15f),
        danger = hsl(5f, 70f, 65f), dangerDeep = hsl(5f, 65f, 60f), dangerBg = hsl(5f, 30f, 15f),
        info = hsl(200f, 60f, 65f), infoBg = hsl(200f, 30f, 15f),
        border100 = hsl(210f, 15f, 22f), border200 = hsl(210f, 15f, 26f), border300 = hsl(210f, 15f, 32f)
    )

    // ============ Claude 暖橙 - 暖调品牌风格 ============
    val claudeLight = ThemeTokens(
        bg000 = hsl(45f, 40f, 99f), bg100 = hsl(45f, 35f, 96f), bg200 = hsl(45f, 30f, 93f),
        bg300 = hsl(45f, 25f, 90f), bg400 = hsl(45f, 20f, 86f),
        text100 = hsl(30f, 10f, 15f), text200 = hsl(30f, 8f, 35f), text300 = hsl(30f, 6f, 50f),
        accent = hsl(24f, 90f, 50f), accentDeep = hsl(24f, 85f, 45f), accentSoft = hsl(24f, 95f, 55f),
        accentSecondary = hsl(210f, 85f, 50f),
        success = hsl(142f, 70f, 40f), successBg = hsl(142f, 60f, 94f),
        warning = hsl(38f, 92f, 48f), warningBg = hsl(48f, 90f, 92f),
        danger = hsl(0f, 72f, 48f), dangerDeep = hsl(0f, 65f, 38f), dangerBg = hsl(0f, 75f, 95f),
        info = hsl(210f, 85f, 48f), infoBg = hsl(210f, 90f, 95f),
        border100 = hsl(35f, 15f, 82f), border200 = hsl(35f, 12f, 85f), border300 = hsl(35f, 18f, 78f)
    )
    val claudeDark = ThemeTokens(
        bg000 = hsl(30f, 3f, 20f), bg100 = hsl(30f, 3f, 15f), bg200 = hsl(30f, 3f, 12f),
        bg300 = hsl(30f, 3f, 9f), bg400 = hsl(0f, 0f, 5f),
        text100 = hsl(40f, 20f, 95f), text200 = hsl(40f, 10f, 75f), text300 = hsl(40f, 5f, 60f),
        accent = hsl(24f, 80f, 58f), accentDeep = hsl(24f, 75f, 50f), accentSoft = hsl(24f, 85f, 62f),
        accentSecondary = hsl(210f, 80f, 60f),
        success = hsl(142f, 70f, 50f), successBg = hsl(142f, 50f, 15f),
        warning = hsl(38f, 90f, 55f), warningBg = hsl(38f, 50f, 15f),
        danger = hsl(0f, 70f, 55f), dangerDeep = hsl(0f, 85f, 65f), dangerBg = hsl(0f, 50f, 15f),
        info = hsl(210f, 85f, 60f), infoBg = hsl(210f, 50f, 15f),
        border100 = hsl(40f, 5f, 25f), border200 = hsl(40f, 5f, 30f), border300 = hsl(40f, 5f, 35f)
    )

    // ============ Breeze 清风 - 现代清新护眼 ============
    val breezeLight = ThemeTokens(
        bg000 = hsl(210f, 20f, 99f), bg100 = hsl(210f, 15f, 96.5f), bg200 = hsl(210f, 12f, 93.5f),
        bg300 = hsl(210f, 10f, 90f), bg400 = hsl(210f, 8f, 86f),
        text100 = hsl(215f, 15f, 14f), text200 = hsl(215f, 10f, 34f), text300 = hsl(215f, 7f, 48f),
        accent = hsl(187f, 72f, 42f), accentDeep = hsl(187f, 68f, 36f), accentSoft = hsl(187f, 75f, 48f),
        accentSecondary = hsl(230f, 65f, 55f),
        success = hsl(152f, 60f, 38f), successBg = hsl(152f, 50f, 94f),
        warning = hsl(42f, 85f, 46f), warningBg = hsl(48f, 80f, 93f),
        danger = hsl(4f, 65f, 46f), dangerDeep = hsl(4f, 60f, 36f), dangerBg = hsl(4f, 65f, 95f),
        info = hsl(215f, 75f, 48f), infoBg = hsl(215f, 80f, 95f),
        border100 = hsl(210f, 10f, 83f), border200 = hsl(210f, 8f, 86f), border300 = hsl(210f, 12f, 78f)
    )
    val breezeDark = ThemeTokens(
        bg000 = hsl(215f, 8f, 20f), bg100 = hsl(215f, 8f, 14f), bg200 = hsl(215f, 8f, 11f),
        bg300 = hsl(215f, 8f, 8f), bg400 = hsl(215f, 10f, 5f),
        text100 = hsl(210f, 15f, 93f), text200 = hsl(210f, 8f, 72f), text300 = hsl(210f, 5f, 58f),
        accent = hsl(187f, 65f, 52f), accentDeep = hsl(187f, 60f, 46f), accentSoft = hsl(187f, 68f, 58f),
        accentSecondary = hsl(230f, 60f, 62f),
        success = hsl(152f, 55f, 48f), successBg = hsl(152f, 40f, 14f),
        warning = hsl(42f, 82f, 52f), warningBg = hsl(42f, 45f, 14f),
        danger = hsl(4f, 65f, 52f), dangerDeep = hsl(4f, 75f, 62f), dangerBg = hsl(4f, 45f, 14f),
        info = hsl(215f, 75f, 58f), infoBg = hsl(215f, 45f, 14f),
        border100 = hsl(215f, 6f, 24f), border200 = hsl(215f, 5f, 28f), border300 = hsl(215f, 7f, 32f)
    )
}

// ============ 主题风格枚举 ============
enum class ThemeStyle(val displayName: String) {
    EUCALYPTUS("Eucalyptus"),
    CLAUDE("Claude"),
    BREEZE("Breeze");

    fun tokens(isDark: Boolean): ThemeTokens = when (this) {
        EUCALYPTUS -> if (isDark) AppThemes.eucalyptusDark else AppThemes.eucalyptusLight
        CLAUDE -> if (isDark) AppThemes.claudeDark else AppThemes.claudeLight
        BREEZE -> if (isDark) AppThemes.breezeDark else AppThemes.breezeLight
    }
}

// ============ 明暗模式偏好 ============
enum class DarkModePref(val displayName: String) {
    LIGHT("浅色"), DARK("深色"), SYSTEM("跟随系统")
}

data class ThemeSettings(val style: ThemeStyle, val darkMode: DarkModePref)

// ============ 主题管理器（SharedPreferences 持久化） ============
object ThemeManager {
    private const val PREF_NAME = "hunterlite_theme"
    private const val KEY_STYLE = "theme_style"
    private const val KEY_DARK = "dark_mode"

    private val _settings = MutableStateFlow(ThemeSettings(ThemeStyle.EUCALYPTUS, DarkModePref.SYSTEM))
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val style = runCatching {
            ThemeStyle.valueOf(prefs.getString(KEY_STYLE, ThemeStyle.EUCALYPTUS.name)!!)
        }.getOrDefault(ThemeStyle.EUCALYPTUS)
        val dark = runCatching {
            DarkModePref.valueOf(prefs.getString(KEY_DARK, DarkModePref.SYSTEM.name)!!)
        }.getOrDefault(DarkModePref.SYSTEM)
        _settings.value = ThemeSettings(style, dark)
    }

    fun setStyle(style: ThemeStyle) = update { it.copy(style = style) }
    fun setDarkMode(mode: DarkModePref) = update { it.copy(darkMode = mode) }

    private fun update(transform: (ThemeSettings) -> ThemeSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString(KEY_STYLE, next.style.name)
            .putString(KEY_DARK, next.darkMode.name)
            .apply()
    }
}

// ============ Token → Material 3 ColorScheme 映射 ============
fun ThemeTokens.toColorScheme(isDark: Boolean): ColorScheme {
    val base = if (isDark) darkColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = accentDeep, onPrimaryContainer = Color.White,
        secondary = accentSecondary, onSecondary = Color.White,
        secondaryContainer = bg000, onSecondaryContainer = text100,
        tertiary = info, onTertiary = Color.White,
        tertiaryContainer = infoBg, onTertiaryContainer = text100,
        background = bg100, onBackground = text100,
        surface = bg100, onSurface = text100,
        surfaceVariant = bg000, onSurfaceVariant = text200,
        surfaceTint = accent,
        inverseSurface = text100, inverseOnSurface = bg100, inversePrimary = accent,
        error = danger, onError = Color.White,
        errorContainer = dangerBg, onErrorContainer = danger,
        outline = border300, outlineVariant = border100,
        scrim = Color.Black
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = accentSoft, onPrimaryContainer = Color.White,
        secondary = accentSecondary, onSecondary = Color.White,
        secondaryContainer = bg300, onSecondaryContainer = text100,
        tertiary = info, onTertiary = Color.White,
        tertiaryContainer = infoBg, onTertiaryContainer = text100,
        background = bg100, onBackground = text100,
        surface = bg000, onSurface = text100,
        surfaceVariant = bg200, onSurfaceVariant = text200,
        surfaceTint = accent,
        inverseSurface = text100, inverseOnSurface = bg100, inversePrimary = accentSoft,
        error = danger, onError = Color.White,
        errorContainer = dangerBg, onErrorContainer = dangerDeep,
        outline = border300, outlineVariant = border100,
        scrim = Color.Black
    )
    // 覆盖 surfaceContainer 系列，确保 TopAppBar / BottomSheet / Card 全部跟随主题
    return base.copy(
        surfaceContainerLowest = if (isDark) bg400 else bg000,
        surfaceContainerLow = if (isDark) bg200 else bg100,
        surfaceContainer = if (isDark) bg100 else bg100,
        surfaceContainerHigh = if (isDark) bg000 else bg200,
        surfaceContainerHighest = if (isDark) bg000 else bg300,
        surfaceBright = bg000
    )
}

// ============ 应用主题入口 ============
@Composable
fun HunterLiteTheme(content: @Composable () -> Unit) {
    val settings by ThemeManager.settings.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (settings.darkMode) {
        DarkModePref.LIGHT -> false
        DarkModePref.DARK -> true
        DarkModePref.SYSTEM -> systemDark
    }

    val colorScheme = settings.style.tokens(isDark).toColorScheme(isDark)

    // 状态栏图标颜色适配
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}