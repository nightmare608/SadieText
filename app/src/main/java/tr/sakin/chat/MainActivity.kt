package tr.sakin.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import tr.sakin.chat.live.*
import tr.sakin.chat.ui.ChatViewModel
import tr.sakin.chat.ui.SakinApp
import tr.sakin.chat.ui.screens.FanIntroScreen
import tr.sakin.chat.ui.theme.SakinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ChatViewModel = viewModel()
            val theme = vm.theme.collectAsStateWithLifecycle()
            SakinTheme(theme.value) {
                var introAccepted by rememberSaveable { mutableStateOf(false) }
                var demo by rememberSaveable { mutableStateOf(false) }
                when {
                    !introAccepted -> FanIntroScreen { introAccepted = true }
                    demo || BuildConfig.OFFLINE_PREVIEW -> SakinApp(vm, directPreview = BuildConfig.OFFLINE_PREVIEW)
                    BuildConfig.HAS_FIREBASE -> {
                        val live: LiveViewModel = viewModel()
                        LiveApp(live, theme.value, vm::theme)
                    }
                    else -> SetupScreen { demo = true }
                }
            }
        }
    }
}
