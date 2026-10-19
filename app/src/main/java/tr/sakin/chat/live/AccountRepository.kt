package tr.sakin.chat.live

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

data class PublicProfile(val uid: String, val name: String, val username: String, val bundle: Map<String, Any>)
fun com.google.firebase.firestore.DocumentSnapshot.profile(): PublicProfile? {
    if (!exists()) return null
    val map = data ?: return null
    @Suppress("UNCHECKED_CAST") val bundle = map["bundle"] as? Map<String, Any> ?: return null
    return PublicProfile(id, getString("name").orEmpty(), getString("username").orEmpty(), bundle)
}
class AccountRepository(val auth: FirebaseAuth, private val db: FirebaseFirestore) {
    suspend fun signIn(email: String, password: String) { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    suspend fun register(email: String, password: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user ?: error("Hesap oluşturulamadı")
        user.sendEmailVerification().await()
    }
    suspend fun resend() { auth.currentUser?.sendEmailVerification()?.await() ?: error("Önce giriş yapın") }
    suspend fun refresh(): Boolean {
        val user = auth.currentUser ?: return false
        user.reload().await(); user.getIdToken(true).await()
        return auth.currentUser?.isEmailVerified == true
    }
    suspend fun reset(email: String) { auth.sendPasswordResetEmail(email.trim()).await() }
    suspend fun deletionPending(): Boolean = db.collection("deletedAccounts").document(auth.currentUser!!.uid).get(Source.SERVER).await().exists()
    suspend fun own(): PublicProfile? = db.collection("users").document(auth.currentUser!!.uid).get(Source.SERVER).await().profile()
    suspend fun claim(name: String, handle: String, bundle: Map<String, Any>) {
        val user = auth.currentUser ?: error("Oturum kapalı")
        check(user.isEmailVerified) { "E-postanızı doğrulayın" }
        require(name.trim().length in 1..40 && Regex("[a-z0-9_]{3,20}").matches(handle))
        val profile = db.collection("users").document(user.uid)
        val username = db.collection("handles").document(handle)
        db.runTransaction { tx ->
            val existing = tx.get(username)
            val self = tx.get(profile)
            check(!self.exists()) { "Profil zaten oluşturulmuş" }
            check(!existing.exists()) { "Bu kullanıcı adı kullanılıyor" }
            tx.set(username, mapOf("uid" to user.uid))
            tx.set(profile, mapOf("name" to name.trim(), "username" to handle, "bundle" to bundle))
        }.await()
    }
    suspend fun replaceDevice(bundle: Map<String, Any>) {
        db.collection("users").document(auth.currentUser!!.uid).update("bundle", bundle).await()
    }
    suspend fun find(handle: String): PublicProfile? {
        val clean = handle.trim().removePrefix("@").lowercase(java.util.Locale.ROOT)
        require(Regex("[a-z0-9_]{3,20}").matches(clean)) { "Tam kullanıcı adını girin" }
        val uid = db.collection("handles").document(clean).get(Source.SERVER).await().getString("uid") ?: return null
        return db.collection("users").document(uid).get(Source.SERVER).await().profile()
    }
    suspend fun profile(uid: String): PublicProfile = db.collection("users").document(uid).get(Source.SERVER).await().profile() ?: error("Bu hesap artık kullanılamıyor")
    suspend fun reauthenticate(password: String) {
        val user = auth.currentUser ?: error("Oturum kapalı")
        val email = user.email ?: error("E-posta bulunamadı")
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        user.getIdToken(true).await()
    }
    suspend fun deleteProfileAndAccount(profile: PublicProfile?) {
        val user = auth.currentUser ?: error("Oturum kapalı")
        if (profile != null) db.runTransaction { tx ->
            val marker = db.collection("deletedAccounts").document(user.uid)
            if (!tx.get(marker).exists()) tx.set(marker, mapOf("deletedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            tx.delete(db.collection("handles").document(profile.username))
            tx.delete(db.collection("users").document(user.uid))
        }.await()
        user.delete().await()
    }
    fun signOut() = auth.signOut()
}
