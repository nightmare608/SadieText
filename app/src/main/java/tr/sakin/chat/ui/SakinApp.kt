package tr.sakin.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import tr.sakin.chat.ui.screens.*
import tr.sakin.chat.games.phone.*

@Composable fun SakinApp(vm: ChatViewModel, directPreview: Boolean = false) {
    val nav = rememberNavController()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val name by vm.displayName.collectAsStateWithLifecycle()
    val username by vm.username.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    fun enter() { nav.navigate("chats") { popUpTo("welcome") { inclusive = true } } }
    fun leave() { vm.reset(); nav.navigate(if (directPreview) "chats" else "welcome") { popUpTo(nav.graph.id) { inclusive = true } } }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("ÇEVRİMDIŞI DEMO · Gerçek mesaj gönderilmez", Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
            NavHost(nav, startDestination = if (directPreview) "chats" else "welcome", modifier = Modifier.weight(1f)) {
                composable("welcome") { WelcomeScreen({ nav.navigate("login") }, { enter() }) }
                composable("login") { AuthScreen(false, { nav.popBackStack() }, { nav.navigate("register") }, { nav.navigate("username") }) }
                composable("register") { AuthScreen(true, { nav.popBackStack() }, { nav.popBackStack() }, { nav.navigate("username") }) }
                composable("username") { UsernameScreen({ nav.popBackStack() }) { n, u -> vm.profile(n, u); enter() } }
                composable("chats") { ChatsScreen(name, conversations, { nav.navigate("search") }, { nav.navigate("settings") }, { nav.navigate("chat/$it") }, { nav.navigate("games") }) }
                composable("games") { GamesScreen({ nav.popBackStack() }, { nav.navigate("phone-company") }) }
                composable("phone-company") { PhoneCompanyScreen("offline-demo", { nav.popBackStack() }) }
                composable("search") { SearchScreen({ nav.popBackStack() }, vm::find) {
                    vm.open(it); nav.navigate("chat/${it.id}") { popUpTo("search") { inclusive = true } }
                } }
                composable("chat/{personId}", arguments = listOf(navArgument("personId") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("personId").orEmpty()
                    val conversation = conversations.firstOrNull { it.person.id == id }
                    ChatScreen(conversation, { nav.popBackStack() }, { vm.send(id, it) }, { vm.delete(id, it) })
                }
                composable("settings") { SettingsScreen(name, username, theme, vm::theme, { nav.popBackStack() }, { leave() }) }
            }
        }
    }
}
