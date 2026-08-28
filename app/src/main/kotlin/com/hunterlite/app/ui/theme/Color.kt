package com.hunterlite.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 将 HSL 颜色转换为 Compose Color
 * 移植自 OpenCodeUI 的 HSL 色板系统 (src/themes/index.ts)
 * @param h 色相 0-360
 * @param s 饱和度 0-100
 * @param l 亮度 0-100
 */
fun hsl(h: Float, s: Float, l: Float): Color {
    val sf = s / 100f
    val lf = l / 100f
    val c = (1 - abs(2 * lf - 1)) * sf
    val x = c * (1 - abs((h / 60f) % 2 - 1))
    val m = lf - c / 2
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}