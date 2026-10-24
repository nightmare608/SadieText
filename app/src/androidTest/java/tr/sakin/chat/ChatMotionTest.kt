package tr.sakin.chat

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tr.sakin.chat.domain.Conversation
import tr.sakin.chat.domain.Message
import tr.sakin.chat.domain.Person
import tr.sakin.chat.ui.screens.ChatScreen
import tr.sakin.chat.ui.theme.SakinTheme
import tr.sakin.chat.data.ThemeMode

@RunWith(AndroidJUnit4::class)
class ChatMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun readingHistoryIsNotInterruptedByIncomingMessage() {
        val chat = mutableStateOf(Conversation(Person("reader","Deniz","deniz"),(0..69).map { Message("id-$it","Mesaj $it",true,"12:00") }))
        compose.setContent { SakinTheme(ThemeMode.LIGHT) { ChatScreen(chat.value,{}, {}, {}) } }
        compose.onNodeWithText("Mesaj 69").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Mesaj 5"))
        compose.onNodeWithText("Mesaj 5").assertIsDisplayed()
        compose.runOnIdle { chat.value = chat.value.copy(messages = chat.value.messages + Message("new-incoming","Yeni gelen mesaj",false,"12:01")) }
        compose.onNodeWithText("Mesaj 5").assertIsDisplayed()
        compose.onNodeWithText("Yeni gelen mesaj").assertIsNotDisplayed()
        compose.onNodeWithText("Son mesaja git ↓").performClick()
        compose.onNodeWithText("Yeni gelen mesaj").assertIsDisplayed()
    }
}
