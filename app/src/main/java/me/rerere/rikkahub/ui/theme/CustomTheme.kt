package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CustomTheme(
    val id: String = Uuid.random().toString(),
    val name: String = "",
    val primaryColorArgb: Long = 0xFF6750A4,
    val secondaryColorArgb: Long? = null,
    val tertiaryColorArgb: Long? = null,
) {
    fun generateColorScheme(dark: Boolean): ColorScheme {
        // Returns a default Material 3 ColorScheme using the configured
        // primary color. The original implementation produced an HCT-based
        // tonal palette via Material Color Utilities; the dynamic-color
        // module is not part of this build, so the secondary/tertiary
        // slots fall back to the primary color.
        val primary = Color(primaryColorArgb.toInt())
        val secondary = secondaryColorArgb?.let { Color(it.toInt()) } ?: primary
        val tertiary = tertiaryColorArgb?.let { Color(it.toInt()) } ?: primary
        return if (dark) {
            darkColorScheme(
                primary = primary,
                secondary = secondary,
                tertiary = tertiary,
            )
        } else {
            lightColorScheme(
                primary = primary,
                secondary = secondary,
                tertiary = tertiary,
            )
        }
    }
}
