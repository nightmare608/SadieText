package tr.sakin.chat.live

import com.google.firebase.firestore.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class ChatTransport(private val db: FirebaseFirestore, private val uid: String) {
    companion object { fun id(a: String, b: String): String {
        val validUid = Regex("[A-Za-z0-9_-]{1,128}")
        require(a != b && validUid.matches(a) && validUid.matches(b)) { "Geçersiz sohbet katılımcısı" }
        return "p2:" + listOf(a, b).sorted().joinToString(":")
    } }
    fun conversations() = callbackFlow<QuerySnapshot> {
        val registration = db.collection("chats").whereArrayContains("members", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snapshot, error -> if (error != null) close(DatabaseFailure(DatabaseOperation.CHAT_LIST, error)) else if (snapshot != null) trySend(snapshot) }
        awaitClose { registration.remove() }
    }
    suspend fun olderConversations(cursor: DocumentSnapshot) = databaseOperation(DatabaseOperation.CHAT_PAGE) {
        db.collection("chats").whereArrayContains("members", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING).startAfter(cursor).limit(50).get(Source.SERVER).await()
    }
    suspend fun open(self: PublicProfile, other: PublicProfile): String = databaseOperation(DatabaseOperation.CHAT_OPEN) {
        val peer = other.uid
        require(peer != uid)
        val id = id(uid, peer)
        // An exact pair query is authorized by membership even when it is empty.
        // Reuse the original document ID: encrypted history/outbox binds to it.
        val existing = db.collection("chats").whereEqualTo("members", listOf(uid, peer).sorted())
            .limit(2).get(Source.SERVER).await()
        val previous = existing.documents.firstOrNull { it.id != id } ?: existing.documents.firstOrNull()
        if (previous != null) return@databaseOperation previous.id
        val ref = db.collection("chats").document(id)
        // Blind, idempotent upsert: absent chats cannot be read under the rules.
        // Creation binds this ID to the pair; an existing chat only changes updatedAt.
        ref.set(mapOf("members" to listOf(uid, peer).sorted(),
            "names" to mapOf(uid to self.username, peer to other.username),
            "updatedAt" to FieldValue.serverTimestamp())).await()
        id
    }
    fun messages(chat: String) = callbackFlow<QuerySnapshot> {
        val registration = collection(chat).orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snapshot, error -> if (error != null) close(DatabaseFailure(DatabaseOperation.MESSAGE_LIST, error)) else if (snapshot != null) trySend(snapshot) }
        awaitClose { registration.remove() }
    }
    fun collection(chat: String) = db.collection("chats").document(chat).collection("messages")
    suspend fun older(chat: String, cursor: DocumentSnapshot): QuerySnapshot = databaseOperation(DatabaseOperation.MESSAGE_PAGE) {
        collection(chat).orderBy("createdAt", Query.Direction.DESCENDING).startAfter(cursor).limit(50).get(Source.SERVER).await()
    }
    suspend fun deliver(envelope: JSONObject): Unit = databaseOperation(DatabaseOperation.MESSAGE_SEND) {
        val chat = envelope.getString("chat"); val doc = collection(chat).document(envelope.getString("id"))
        db.runTransaction { tx ->
            val old = tx.get(doc)
            if (old.exists()) {
                check(old.getString("ciphertext") == envelope.getString("ciphertext") && old.getString("sender") == uid) { "Mesaj kimliği çakışması" }
            } else {
                tx.set(doc, mapOf("sender" to uid, "recipient" to envelope.getString("recipient"),
                    "senderIdentity" to envelope.getString("senderIdentity"), "recipientIdentity" to envelope.getString("targetIdentity"),
                    "type" to envelope.getInt("type"), "ciphertext" to envelope.getString("ciphertext"),
                    "createdAt" to FieldValue.serverTimestamp(), "readAt" to null))
                tx.update(db.collection("chats").document(chat), "updatedAt", FieldValue.serverTimestamp())
            }
        }.await()
    }
    suspend fun markRead(doc: DocumentSnapshot): Unit = databaseOperation(DatabaseOperation.MESSAGE_READ) {
        if (doc.getString("recipient") == uid && doc.getTimestamp("readAt") == null)
            doc.reference.update("readAt", FieldValue.serverTimestamp()).await()
    }
    suspend fun delete(chat: String, message: String): Unit = databaseOperation(DatabaseOperation.MESSAGE_DELETE) {
        val ref = collection(chat).document(message)
        db.runTransaction { tx ->
            val document = tx.get(ref)
            if (document.exists()) {
                check(document.getString("sender") == uid) { "Yalnızca gönderdiğiniz mesajı silebilirsiniz." }
                tx.delete(ref)
            }
        }.await()
    }
    suspend fun deleteSentMessages(): Unit = databaseOperation(DatabaseOperation.ACCOUNT_DELETE) {
        // Paginate both levels; only sender-owned ciphertext is deleted.
        var cursor: DocumentSnapshot? = null
        while (true) {
            var query: Query = db.collection("chats").whereArrayContains("members", uid).orderBy(FieldPath.documentId()).limit(30)
            cursor?.let { query = query.startAfter(it) }
            val chats = query.get(Source.SERVER).await()
            for (chat in chats.documents) {
                while (true) {
                    val docs = collection(chat.id).whereEqualTo("sender", uid).limit(100).get(Source.SERVER).await()
                    if (docs.isEmpty) break
                    val batch = db.batch(); docs.forEach { batch.delete(it.reference) }; batch.commit().await()
                }
            }
            if (chats.size() < 30) break
            cursor = chats.documents.last()
        }
    }
}
