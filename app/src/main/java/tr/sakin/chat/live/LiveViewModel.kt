package tr.sakin.chat.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import tr.sakin.chat.crypto.*
import java.util.UUID
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val PAGE_SIZE = 50
enum class LivePage { LOADING, AUTH, VERIFY, PROFILE, DEVICE, DELETE_PENDING, HOME, SEARCH, CHAT, SETTINGS, ERROR }
data class ChatRow(val id: String, val peer: String, val handle: String)
data class MessageRow(val id: String, val text: String, val mine: Boolean, val time: String, val status: String, val deletable: Boolean)
data class LiveState(
    val page: LivePage = LivePage.LOADING, val busy: Boolean = false, val error: String? = null,
    val notice: String? = null, val me: PublicProfile? = null, val peer: PublicProfile? = null,
    val chatId: String? = null, val chats: List<ChatRow> = emptyList(), val messages: List<MessageRow> = emptyList(),
    val found: PublicProfile? = null, val searched: Boolean = false, val safety: String = "", val verified: Boolean = false,
    val online: Boolean = true, val more: Boolean = true, val pending: Int = 0, val moreChats: Boolean = true,
    val listenerRevision: Int = 0)

class LiveViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
    }
    private val accounts = AccountRepository(auth, db)
    private val mutable = MutableStateFlow(LiveState())
    val state = mutable.asStateFlow()
    private var engine: SignalEngine? = null
    private var vault: Vault? = null
    private var transport: ChatTransport? = null
    private var sessionJob: Job? = null
    private val receipts = mutableSetOf<String>()
    private val chatHistory = mutableMapOf<String, DocumentSnapshot>()
    private var recentChats = emptyList<DocumentSnapshot>()
    private var chatCursor: DocumentSnapshot? = null
    private val history = mutableMapOf<String, DocumentSnapshot>()
    private var newest = emptyList<DocumentSnapshot>()
    private var cursor: DocumentSnapshot? = null
    private val outboxLock = kotlinx.coroutines.sync.Mutex()
    private val authListener = FirebaseAuth.AuthStateListener {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch { initialize() }
    }
    init {
        auth.addAuthStateListener(authListener)
        viewModelScope.launch {
            NetworkMonitor(app).online.collect { online ->
                mutable.update { it.copy(online = online) }
                if (online && engine != null) flush()
            }
        }
    }
    private fun update(block: (LiveState) -> LiveState) = mutable.update(block)
    private fun operation(block: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            update { it.copy(busy = true, error = null, notice = null) }
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(error = friendly(e)) } }
            finally { update { it.copy(busy = false) } }
        }
    }
    private suspend fun initialize() {
        engine = null; transport = null; vault = null; history.clear(); receipts.clear(); chatHistory.clear(); recentChats = emptyList(); chatCursor = null; newest = emptyList(); cursor = null
        mutable.value = LiveState(page = LivePage.LOADING, online = state.value.online)
        val user = auth.currentUser
        if (user == null) { update { it.copy(page = LivePage.AUTH) }; return }
        try {
            if (!accounts.refresh()) { update { it.copy(page = LivePage.VERIFY) }; return }
            val uid = user.uid
            val profile = accounts.own()
            if (profile == null && accounts.deletionPending()) {
                vault = Vault(getApplication(), uid)
                update { it.copy(page = LivePage.DELETE_PENDING) }
                return
            }
            val localVault = Vault(getApplication(), uid)
            val localEngine = withContext(Dispatchers.IO) { SignalEngine(uid, localVault::read, localVault::write) }
            vault = localVault; engine = localEngine; transport = ChatTransport(db, uid)
            val bundle = withContext(Dispatchers.IO) { localEngine.publicBundle() }
            if (profile != null && profile.bundle["identity"] == bundle["identity"] && profile.bundle != bundle) accounts.replaceDevice(bundle)
            val loadedProfile = if (profile != null && profile.bundle["identity"] == bundle["identity"]) profile.copy(bundle = bundle) else profile
            update { it.copy(me = loadedProfile, pending = localEngine.pending().size, page = when {
                profile == null -> LivePage.PROFILE
                profile.bundle["identity"] != bundle["identity"] -> LivePage.DEVICE
                else -> LivePage.HOME
            }) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { update { it.copy(page = LivePage.ERROR, error = friendly(e)) } }
    }
    fun login(email: String, password: String, register: Boolean) = operation {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Geçerli e-posta girin" }
        require(password.length >= 8) { "Şifre en az 8 karakter olmalı" }
        if (register) accounts.register(email, password) else accounts.signIn(email, password)
    }
    fun reset(email: String) = operation { accounts.reset(email); update { it.copy(notice = "Adres uygunsa şifre sıfırlama e-postası gönderildi.") } }
    fun resend() = operation { accounts.resend(); update { it.copy(notice = "Doğrulama e-postası gönderildi.") } }
    fun verifiedEmail() = operation { if (accounts.refresh()) initialize() else update { it.copy(error = "E-posta henüz doğrulanmamış.") } }
    fun retry() = operation { initialize() }
    fun retryListeners() {
        if (!state.value.busy) update { it.copy(error = null, listenerRevision = it.listenerRevision + 1) }
    }
    fun resetLocalKeys() = operation {
        val uid = auth.currentUser?.uid ?: error("Önce giriş yapın")
        withContext(Dispatchers.IO) { Vault(getApplication(), uid).delete() }
        initialize()
    }
    fun claim(name: String, handle: String) = operation {
        accounts.claim(name, handle, withContext(Dispatchers.IO) { engine!!.publicBundle() })
        val own = accounts.own()
        update { it.copy(me = own, page = LivePage.HOME) }
    }
    fun replaceDevice(password: String) = operation {
        accounts.reauthenticate(password)
        accounts.replaceDevice(withContext(Dispatchers.IO) { engine!!.publicBundle() })
        val own = accounts.own()
        update { it.copy(me = own, page = LivePage.HOME, notice = "Yeni cihaz kaydedildi. Arkadaşlarınız anahtarınızı yeniden doğrulamalı.") }
    }
    fun go(page: LivePage) {
        if (state.value.busy) return
        update { it.copy(page = page, error = null, notice = null, found = null, searched = false) }
    }
    fun search(handle: String) = operation {
        val person = accounts.find(handle)
        update { it.copy(found = person?.takeIf { p -> p.uid != auth.currentUser?.uid }, searched = true) }
    }
    fun open(peer: String) = operation {
        val person = accounts.profile(peer)
        val id = transport!!.open(state.value.me!!, person)
        history.clear(); newest = emptyList(); cursor = null
        val safety = withContext(Dispatchers.Default) { engine!!.safetyNumber(peer, person.bundle) }
        update { it.copy(page = LivePage.CHAT, peer = person, chatId = id, messages = emptyList(), safety = safety, verified = engine!!.isVerified(peer, person.bundle), more = true) }
    }
    fun verify() = operation {
        val old = state.value.peer ?: return@operation
        val fresh = accounts.profile(old.uid)
        check(fresh.bundle["identity"] == old.bundle["identity"]) { "Anahtar karşılaştırma sırasında değişti. Sohbeti yeniden açın." }
        withContext(Dispatchers.IO) { engine!!.verify(fresh.uid, fresh.bundle) }
        update { it.copy(verified = true) }; renderMessages()
    }
    suspend fun observeHome() {
        flush()
        try { transport?.conversations()?.collect { snapshot ->
            recentChats = snapshot.documents
            if (chatHistory.isEmpty()) chatCursor = recentChats.lastOrNull()
            renderChats()
        } } catch (e: CancellationException) { throw e } catch (e: Exception) { update { it.copy(error = friendly(e)) } }
    }
    private fun renderChats() {
        val uid = auth.currentUser?.uid ?: return
        val docs = (recentChats + chatHistory.values).distinctBy { it.id }.sortedByDescending { it.getTimestamp("updatedAt") }
        update { it.copy(chats = docs.mapNotNull { doc ->
            val peer = (doc.get("members") as? List<*>)?.filterIsInstance<String>()?.firstOrNull { p -> p != uid } ?: return@mapNotNull null
            ChatRow(doc.id, peer, (doc.get("names") as? Map<*, *>)?.get(peer) as? String ?: peer.take(8))
        }) }
    }
    fun olderChats() = operation {
        val last = chatCursor ?: run { update { it.copy(moreChats = false) }; return@operation }
        val docs = transport!!.olderConversations(last).documents
        docs.forEach { chatHistory[it.id] = it }; chatCursor = docs.lastOrNull() ?: last
        update { it.copy(moreChats = docs.size == PAGE_SIZE) }; renderChats()
    }
    suspend fun observeChat() {
        val chat = state.value.chatId ?: return
        try { transport!!.messages(chat).collect { snapshot ->
            newest = snapshot.documents
            val currentPeer = state.value.peer
            if (currentPeer != null && newest.any { it.getString("sender") == currentPeer.uid && it.getString("senderIdentity") != currentPeer.bundle["identity"] }) {
                val fresh = accounts.profile(currentPeer.uid)
                if (fresh.bundle["identity"] != currentPeer.bundle["identity"]) {
                    val number = withContext(Dispatchers.Default) { engine!!.safetyNumber(fresh.uid, fresh.bundle) }
                    update { it.copy(peer = fresh, verified = engine!!.isVerified(fresh.uid, fresh.bundle), safety = number) }
                }
            }
            if (history.isEmpty()) cursor = newest.lastOrNull()
            renderMessages()
        } } catch (e: CancellationException) { throw e } catch (e: Exception) { update { it.copy(error = friendly(e)) } }
    }
    private suspend fun renderMessages() {
        val current = state.value; val peer = current.peer ?: return; val chat = current.chatId ?: return
        val localEngine = engine ?: return
        val uid = auth.currentUser?.uid ?: return
        val docs = (newest + history.values).distinctBy { it.id }.sortedWith(compareBy<DocumentSnapshot> { it.getTimestamp("createdAt") }.thenBy { it.id })
        val rows = mutableListOf<MessageRow>()
        for (doc in docs) {
            val mine = doc.getString("sender") == uid
            val text = withContext(Dispatchers.IO) {
                localEngine.cached(doc.id, chat, doc.getString("sender")) ?: if (mine) "Bu mesajın yerel kopyası bu cihazda yok."
                else if (doc.getString("senderIdentity") != peer.bundle["identity"] || doc.getString("recipientIdentity") != localEngine.publicBundle()["identity"]) "Bu mesaj farklı bir cihaz anahtarına ait."
                else if (!current.verified) "Güvenlik numarasını doğrulayın."
                else try { localEngine.decrypt(doc.id, chat, peer.uid, doc.getLong("type")!!.toInt(), doc.getString("ciphertext")!!, peer.bundle) }
                catch (_: Exception) { "Mesaj açılamadı. Anahtarı kontrol edin veya daha eski mesajları yükleyin." }
            }
            val decrypted = localEngine.cached(doc.id, chat, doc.getString("sender")) != null
            if (!mine && decrypted && doc.getTimestamp("readAt") == null && receipts.add("$chat:${doc.id}")) {
                try { transport!!.markRead(doc) } catch (e: CancellationException) { receipts.remove("$chat:${doc.id}"); throw e }
                catch (e: Exception) { receipts.remove("$chat:${doc.id}"); update { it.copy(error = friendly(e)) } }
            }
            val time = doc.getTimestamp("createdAt")?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty()
            rows += MessageRow(doc.id, text, mine, time, if (!mine) "" else if (doc.getTimestamp("readAt") != null) "Okundu" else "Gönderildi", mine)
        }
        val ids = docs.map { it.id }.toSet()
        val pending = withContext(Dispatchers.IO) { localEngine.pending() }
        pending.filter { it.getString("chat") == chat && it.getString("id") !in ids }.forEach { e ->
            rows += MessageRow(e.getString("id"), localEngine.cached(e.getString("id"), chat, uid).orEmpty(), true, "", "Bekliyor", true)
        }
        if (state.value.chatId == chat) update { it.copy(messages = rows, pending = pending.size) }
    }
    fun older() = operation {
        val last = cursor ?: run { update { it.copy(more = false) }; return@operation }
        val docs = transport!!.older(state.value.chatId!!, last).documents
        docs.forEach { history[it.id] = it }; cursor = docs.lastOrNull() ?: last
        update { it.copy(more = docs.size == PAGE_SIZE) }; renderMessages()
    }
    fun send(text: String, accepted: () -> Unit) = operation {
        val current = state.value; val peer = current.peer ?: return@operation
        // Encryption can be queued offline only against the already-verified cached identity.
        val fresh = if (current.online) accounts.profile(peer.uid) else peer
        if (current.online) check(accounts.own()?.bundle?.get("identity") == engine!!.publicBundle()["identity"]) { "Hesabınız başka cihazda etkinleştirildi. Çıkış yapıp yeniden giriş yapın." }
        if (!engine!!.isVerified(peer.uid, fresh.bundle)) {
            val number = withContext(Dispatchers.Default) { engine!!.safetyNumber(peer.uid, fresh.bundle) }
            update { it.copy(peer = fresh, verified = false, safety = number) }; error("Karşı tarafın anahtarı değişti. Yeniden doğrulayın.")
        }
        withContext(Dispatchers.IO) { engine!!.encrypt(UUID.randomUUID().toString(), current.chatId!!, peer.uid, fresh.bundle, text) }
        accepted(); renderMessages(); flush()
    }
    fun retryOutbox() = operation { flush(); if (state.value.page == LivePage.CHAT) renderMessages() }
    private suspend fun flush() {
        if (!state.value.online || !outboxLock.tryLock()) return
        try {
            val localEngine = engine ?: return
            val remote = transport ?: return
            if (state.value.me == null) return
            if (state.value.page == LivePage.DEVICE) return
            check(accounts.own()?.bundle?.get("identity") == localEngine.publicBundle()["identity"]) { "Bu cihazın anahtarı artık etkin değil." }
            for (envelope in withContext(Dispatchers.IO) { localEngine.pending() }) {
                val peer = accounts.profile(envelope.getString("recipient"))
                check(localEngine.isVerified(peer.uid, peer.bundle) && envelope.getString("targetIdentity") == peer.bundle["identity"]) { "Bekleyen mesaj için karşı tarafın anahtarı değişti. Eski taslağı silip yeniden doğrulayın." }
                remote.deliver(envelope)
                withContext(Dispatchers.IO) { localEngine.acknowledge(envelope.getString("id")) }
            }
            update { it.copy(pending = localEngine.pending().size) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { update { it.copy(error = friendly(e)) } }
        finally { outboxLock.unlock() }
    }
    fun deleteMessage(id: String) = operation {
        check(state.value.online) { "Mesaj silmek için internete bağlanın." }
        val chat = state.value.chatId ?: return@operation
        outboxLock.withLock {
            transport!!.delete(chat, id)
            withContext(Dispatchers.IO) { engine!!.forgetMessage(id, chat) }
        }
        history.remove(id); newest = newest.filterNot { it.id == id }; renderMessages()
    }
    fun signOut() { if (!state.value.busy) accounts.signOut() }
    fun deleteAccount(password: String) = operation {
        val localVault = vault
        outboxLock.withLock {
            accounts.reauthenticate(password)
            val existing = accounts.own()
            if (existing != null) transport?.deleteSentMessages()
            accounts.deleteProfileAndAccount(existing)
            withContext(Dispatchers.IO) { localVault?.delete() }
        }
    }
    override fun onCleared() { auth.removeAuthStateListener(authListener); super.onCleared() }
}
fun friendly(error: Exception): String {
    FirestoreDiagnostics.describe(error)?.let { issue ->
        android.util.Log.w("SadieTextDb", issue.logLine)
        return issue.userMessage
    }
    return when (error) {
    is com.google.firebase.FirebaseTooManyRequestsException -> "Çok fazla istek yapıldı. Bir süre bekleyip yeniden deneyin."
    is com.google.firebase.FirebaseNetworkException -> "İnternete ulaşılamıyor. Bağlantınızı kontrol edin."
    is FirebaseAuthException -> when (error.errorCode) {
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Bu e-posta zaten kullanılıyor."
        "ERROR_TOO_MANY_REQUESTS" -> "Çok fazla deneme yapıldı. Biraz bekleyin."
        "ERROR_WEAK_PASSWORD" -> "Daha güçlü bir şifre seçin."
        "ERROR_USER_DISABLED" -> "Bu hesap devre dışı."
        else -> "Giriş işlemi tamamlanamadı. E-posta ve şifrenizi kontrol edin."
    }
    is IllegalArgumentException, is IllegalStateException -> error.message ?: "İşlem tamamlanamadı."
    else -> "İşlem tamamlanamadı. Bağlantınızı ve uygulama yapılandırmasını kontrol edin."
    }
}
