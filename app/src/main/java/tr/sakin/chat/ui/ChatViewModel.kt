package tr.sakin.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tr.sakin.chat.data.*
import tr.sakin.chat.domain.ChatRepository

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: ChatRepository = DemoChatRepository()
    private val themes = ThemeStore(app)
    val conversations = repository.conversations
    val theme = themes.mode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
    val displayName = MutableStateFlow("Misafir")
    val username = MutableStateFlow("misafir")
    fun profile(name: String, handle: String) { displayName.value = name.trim(); username.value = handle.trim() }
    fun find(query: String) = repository.find(query)
    fun open(person: tr.sakin.chat.domain.Person) = repository.open(person)
    fun send(id: String, text: String) = repository.addLocalMessage(id, text)
    fun delete(id: String, message: String) = repository.deleteLocalMessage(id, message)
    fun theme(mode: ThemeMode) { viewModelScope.launch { themes.set(mode) } }
    fun reset() { repository.clear(); displayName.value = "Misafir"; username.value = "misafir" }
}
