package tr.sakin.chat.crypto

import org.json.JSONObject
import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.ecc.*
import org.signal.libsignal.protocol.kem.*
import org.signal.libsignal.protocol.state.*
import org.signal.libsignal.protocol.message.*
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator
import org.signal.libsignal.protocol.util.KeyHelper

/** One serialized owner per account. No plaintext is returned before state+cache commit. */
class SignalEngine(private val uid: String, load: () -> JSONObject, private val persist: (JSONObject) -> Unit) {
    private val store = SignalStore(load())
    private val local = SignalProtocolAddress(uid, 1)
    init {
        if (!store.root.has("identity")) {
            val identity = IdentityKeyPair.generate()
            val ec = ECKeyPair.generate()
            val kem = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
            store.root.put("identity", identity.serialize().b64()).put("registration", KeyHelper.generateRegistrationId(false))
            store.storeSignedPreKey(1, SignedPreKeyRecord(1, System.currentTimeMillis(), ec, identity.privateKey.calculateSignature(ec.publicKey.serialize())))
            store.storeKyberPreKey(1, KyberPreKeyRecord(1, System.currentTimeMillis(), kem, identity.privateKey.calculateSignature(kem.publicKey.serialize())))
            store.root.put("activePreKey", 1)
            persist(store.root)
        }
        rotatePreKeysIfNeeded()
    }
    @Synchronized private fun rotatePreKeysIfNeeded() {
        val oldId = store.root.optInt("activePreKey", 1)
        if (System.currentTimeMillis() - store.loadSignedPreKey(oldId).timestamp < 7L * 24 * 60 * 60 * 1000) return
        atomic {
            val id = oldId + 1
            val ec = ECKeyPair.generate(); val kem = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
            val identity = store.getIdentityKeyPair()
            store.storeSignedPreKey(id, SignedPreKeyRecord(id, System.currentTimeMillis(), ec, identity.privateKey.calculateSignature(ec.publicKey.serialize())))
            store.storeKyberPreKey(id, KyberPreKeyRecord(id, System.currentTimeMillis(), kem, identity.privateKey.calculateSignature(kem.publicKey.serialize())))
            store.root.put("activePreKey", id)
            // Retain 8 weekly generations for delayed first-contact messages.
            store.loadSignedPreKeys().filter { it.id <= id - 8 }.forEach { record ->
                store.removeSignedPreKey(record.id)
                store.root.optJSONObject("kyber")?.remove(record.id.toString())
                store.root.optJSONObject("usedKyber")?.let { used ->
                    used.keys().asSequence().toList().filter { it.startsWith("${record.id}:") }.forEach { used.remove(it) }
                }
            }
        }
    }
    private inline fun <T> atomic(block: () -> T): T {
        val old = store.root.toString()
        try { val result = block(); persist(store.root); return result }
        catch (e: Exception) { store.root = JSONObject(old); throw e }
    }
    private fun section(name: String) = store.root.optJSONObject(name) ?: JSONObject().also { store.root.put(name, it) }
    @Synchronized fun publicBundle(): Map<String, Any> {
        val id = store.root.optInt("activePreKey", 1)
        val ec = store.loadSignedPreKey(id); val kem = store.loadKyberPreKey(id)
        return mapOf("signedId" to id.toLong(), "kyberId" to id.toLong(), "identity" to store.getIdentityKeyPair().publicKey.serialize().b64(), "registration" to store.getLocalRegistrationId().toLong(),
            "signedKey" to ec.keyPair.publicKey.serialize().b64(), "signedSignature" to ec.signature.b64(),
            "kyberKey" to kem.keyPair.publicKey.serialize().b64(), "kyberSignature" to kem.signature.b64())
    }
    private fun remoteKey(bundle: Map<String, Any>) = IdentityKey((bundle.getValue("identity") as String).unb64())
    private fun bundle(data: Map<String, Any>) = PreKeyBundle(
        (data.getValue("registration") as Number).toInt(), 1, PreKeyBundle.NULL_PRE_KEY_ID, null,
        (data.getValue("signedId") as Number).toInt(), ECPublicKey((data.getValue("signedKey") as String).unb64()), (data.getValue("signedSignature") as String).unb64(), remoteKey(data),
        (data.getValue("kyberId") as Number).toInt(), KEMPublicKey((data.getValue("kyberKey") as String).unb64()), (data.getValue("kyberSignature") as String).unb64())
    @Synchronized fun safetyNumber(peer: String, data: Map<String, Any>): String = NumericFingerprintGenerator(5200)
        .createFor(2, uid.toByteArray(Charsets.UTF_8), store.getIdentityKeyPair().publicKey,
            peer.toByteArray(Charsets.UTF_8), remoteKey(data)).displayableFingerprint.displayText.chunked(5).joinToString(" ")
    @Synchronized fun isVerified(peer: String, data: Map<String, Any>) = store.getIdentity(SignalProtocolAddress(peer, 1)) == remoteKey(data)
    @Synchronized fun verify(peer: String, data: Map<String, Any>) { atomic { store.trust(SignalProtocolAddress(peer, 1), remoteKey(data)) } }
    @Synchronized fun encrypt(id: String, chat: String, peer: String, data: Map<String, Any>, text: String): JSONObject {
        require(text.isNotBlank() && text.length <= 2000)
        section("outbox").optJSONObject(id)?.let { return JSONObject(it.toString()) }
        check(isVerified(peer, data)) { "Önce iki cihazda güvenlik numarasını karşılaştırıp doğrulayın." }
        return atomic {
            val remote = SignalProtocolAddress(peer, 1)
            if (!store.containsSession(remote)) SessionBuilder(store, remote, local).process(bundle(data))
            val clear = JSONObject().put("id", id).put("chat", chat).put("sender", uid).put("recipient", peer).put("senderIdentity", publicBundle().getValue("identity")).put("recipientIdentity", data.getValue("identity")).put("text", text)
            val encrypted = SessionCipher(store, local, remote).encrypt(clear.toString().toByteArray(Charsets.UTF_8))
            val envelope = JSONObject().put("id", id).put("chat", chat).put("sender", uid).put("recipient", peer)
                .put("targetIdentity", data.getValue("identity")).put("senderIdentity", publicBundle().getValue("identity"))
                .put("type", encrypted.type).put("ciphertext", encrypted.serialize().b64())
            section("outbox").put(id, envelope)
            section("cache").put("$chat:$id", clear)
            JSONObject(envelope.toString())
        }
    }
    @Synchronized fun cached(id: String, chat: String, sender: String? = null): String? {
        val payload = section("cache").optJSONObject("$chat:$id") ?: return null
        if (payload.optString("id") != id || payload.optString("chat") != chat || (sender != null && payload.optString("sender") != sender)) return null
        return payload.optString("text")
    }
    @Synchronized fun decrypt(id: String, chat: String, sender: String, type: Int, ciphertext: String, data: Map<String, Any>): String {
        cached(id, chat, sender)?.let { return it }
        check(isVerified(sender, data)) { "Kimlik doğrulanmadı veya anahtar değişti. Güvenlik numarasını karşılaştırın." }
        return atomic {
            val cipher = SessionCipher(store, local, SignalProtocolAddress(sender, 1))
            val bytes = when (type) {
                CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(ciphertext.unb64()))
                CiphertextMessage.WHISPER_TYPE -> cipher.decrypt(SignalMessage(ciphertext.unb64()))
                else -> error("Desteklenmeyen şifreli mesaj biçimi")
            }
            val payload = JSONObject(bytes.toString(Charsets.UTF_8))
            check(payload.getString("id") == id && payload.getString("chat") == chat && payload.getString("sender") == sender && payload.getString("recipient") == uid) { "Mesaj bağlamı doğrulanamadı." }
            check(payload.getString("senderIdentity") == data["identity"] && payload.getString("recipientIdentity") == publicBundle()["identity"]) { "Mesaj cihaz anahtarları eşleşmiyor." }
            val text = payload.getString("text")
            require(text.length <= 2000)
            section("cache").put("$chat:$id", payload)
            text
        }
    }
    @Synchronized fun pending(): List<JSONObject> = section("outbox").keys().asSequence().map { JSONObject(section("outbox").getJSONObject(it).toString()) }.toList()
    @Synchronized fun acknowledge(id: String) { atomic { section("outbox").remove(id) } }
    @Synchronized fun forgetMessage(id: String, chat: String) { atomic { section("cache").remove("$chat:$id"); section("outbox").remove(id) } }
}
