package tr.sakin.chat.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tr.sakin.chat.domain.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class DemoChatRepository : ChatRepository {
    private val people = listOf(Person("deniz", "Deniz", "deniz"), Person("ece", "Ece", "ece"))
    private val state = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversations = state.asStateFlow()
    override fun find(username: String) = people.firstOrNull { it.username == username.trim().removePrefix("@").lowercase(java.util.Locale.ROOT) }
    override fun open(person: Person) { state.update { list ->
        if (list.any { it.person.id == person.id }) list else list + Conversation(person, emptyList())
    } }
    override fun addLocalMessage(personId: String, text: String) {
        val clean = text.trim().take(2000)
        if (clean.isEmpty()) return
        val message = Message(UUID.randomUUID().toString(), clean, true,
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
        state.update { list -> list.map { if (it.person.id == personId) it.copy(messages = it.messages + message) else it } }
    }
    override fun deleteLocalMessage(personId: String, messageId: String) {
        state.update { list -> list.map { if (it.person.id == personId) it.copy(messages = it.messages.filterNot { m -> m.id == messageId }) else it } }
    }
    override fun clear() { state.value = emptyList() }
}
