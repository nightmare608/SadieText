package tr.sakin.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import tr.sakin.chat.data.ThemeMode

private val Light = lightColorScheme(primary = Color(0xFF176B50), onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F5E2), onPrimaryContainer = Color(0xFF062B20),
    background = Color(0xFFF6F9F6), surface = Color(0xFFF6F9F6), surfaceVariant = Color(0xFFE5EDE7))
private val Dark = darkColorScheme(primary = Color(0xFF91DCB9), onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF19513D), onPrimaryContainer = Color(0xFFD2F5E2),
    background = Color(0xFF101916), surface = Color(0xFF101916), surfaceVariant = Color(0xFF25372E))
@Composable fun SakinTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
