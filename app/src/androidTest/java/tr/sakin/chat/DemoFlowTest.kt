package tr.sakin.chat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun demoConversationCanBeOpenedEditedAndDeleted() {
        org.junit.Assume.assumeFalse("Demo flow is only available without Firebase configuration", BuildConfig.HAS_FIREBASE)
        compose.onAllNodesWithText("Kabul et")[0].performClick()
        if (!BuildConfig.OFFLINE_PREVIEW) {
            compose.onNodeWithText("Arayüz demosunu aç").performClick()
            compose.onNodeWithText("Demoyu keşfet").performClick()
        }
        compose.onNodeWithText("Yeni sohbet").performClick()
        compose.onNodeWithText("Tam kullanıcı adı").performTextInput("deniz")
        compose.onNodeWithText("Deniz").performClick()
        compose.onNodeWithText("Mesaj yaz…").performTextInput("Merhaba Deniz")
        compose.onNodeWithContentDescription("Demo mesaj ekle").performClick()
        compose.onNodeWithText("Merhaba Deniz").assertIsDisplayed().performClick()
        compose.onNodeWithText("Sil", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Merhaba Deniz").assertDoesNotExist()
        compose.onNodeWithContentDescription("Geri").performClick()
        compose.onNodeWithText("Sohbetler").assertIsDisplayed()
        compose.onNodeWithText("Deniz").assertIsDisplayed()
    }
    @Test fun offlinePreviewHasNoNetworkPermissionOrFirebaseInitializer() {
        org.junit.Assume.assumeTrue(BuildConfig.OFFLINE_PREVIEW)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS or android.content.pm.PackageManager.GET_PROVIDERS)
        org.junit.Assert.assertEquals("tr.sakin.chat.offline", context.packageName)
        org.junit.Assert.assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        org.junit.Assert.assertFalse(info.providers.orEmpty().any { it.name == "com.google.firebase.provider.FirebaseInitProvider" })
    }
}
