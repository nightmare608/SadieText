package tr.sakin.chat.domain

data class Person(val id: String, val name: String, val username: String)
data class Message(val id: String, val text: String, val mine: Boolean, val time: String)
data class Conversation(val person: Person, val messages: List<Message>)
// Stage 1 only: no network, no encryption claims, no persisted message contents.
interface ChatRepository {
    val conversations: kotlinx.coroutines.flow.StateFlow<List<Conversation>>
    fun find(username: String): Person?
    fun open(person: Person)
    fun addLocalMessage(personId: String, text: String)
    fun deleteLocalMessage(personId: String, messageId: String)
    fun clear()
}
