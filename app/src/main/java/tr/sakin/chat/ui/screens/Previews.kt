package tr.sakin.chat.ui.screens

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tr.sakin.chat.data.ThemeMode
import tr.sakin.chat.ui.theme.SakinTheme

@Preview(name = "Açık tema", showBackground = true, locale = "tr")
@Composable private fun WelcomeLightPreview() {
    SakinTheme(ThemeMode.LIGHT) { Surface { WelcomeScreen({}, {}) } }
}
@Preview(name = "Koyu tema", showBackground = true, locale = "tr")
@Composable private fun WelcomeDarkPreview() {
    SakinTheme(ThemeMode.DARK) { Surface { WelcomeScreen({}, {}) } }
}
