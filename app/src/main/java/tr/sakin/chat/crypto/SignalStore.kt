package tr.sakin.chat.crypto

import org.json.JSONObject
import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.*
import java.util.Base64
import java.util.UUID

internal fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)
internal fun String.unb64(): ByteArray = Base64.getDecoder().decode(this)

/** Serialized records are always copied. The owner commits protocol state + message atomically. */
class SignalStore(var root: JSONObject) : SignalProtocolStore {
    private fun section(name: String): JSONObject = root.optJSONObject(name) ?: JSONObject().also { root.put(name, it) }
    private fun address(a: SignalProtocolAddress) = "${a.name}:${a.deviceId}"
    private fun bytes(group: String, key: String): ByteArray? = section(group).optString(key).takeIf { it.isNotEmpty() }?.unb64()
    private fun put(group: String, key: String, bytes: ByteArray) { section(group).put(key, bytes.b64()) }
    override fun getIdentityKeyPair() = IdentityKeyPair(root.getString("identity").unb64())
    override fun getLocalRegistrationId() = root.getInt("registration")
    override fun getIdentity(a: SignalProtocolAddress): IdentityKey? = bytes("identities", address(a))?.let { IdentityKey(it) }
    override fun saveIdentity(a: SignalProtocolAddress, key: IdentityKey): IdentityKeyStore.IdentityChange {
        val old = getIdentity(a)
        check(old == null || old == key) { "Kimlik anahtarı değişti. Güvenlik numarasını yeniden doğrulayın." }
        put("identities", address(a), key.serialize())
        return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }
    override fun isTrustedIdentity(a: SignalProtocolAddress, key: IdentityKey, direction: IdentityKeyStore.Direction) = getIdentity(a) == key
    fun trust(a: SignalProtocolAddress, key: IdentityKey) {
        if (getIdentity(a) != key) deleteSession(a)
        put("identities", address(a), key.serialize())
    }
    override fun loadSession(a: SignalProtocolAddress) = bytes("sessions", address(a))?.let { SessionRecord(it) } ?: SessionRecord()
    override fun storeSession(a: SignalProtocolAddress, record: SessionRecord) = put("sessions", address(a), record.serialize())
    override fun containsSession(a: SignalProtocolAddress) = section("sessions").has(address(a))
    override fun deleteSession(a: SignalProtocolAddress) { section("sessions").remove(address(a)) }
    override fun deleteAllSessions(name: String) { section("sessions").keys().asSequence().toList().filter { it.startsWith("$name:") }.forEach { section("sessions").remove(it) } }
    override fun getSubDeviceSessions(name: String): List<Int> = section("sessions").keys().asSequence().filter { it.startsWith("$name:") }.map { it.substringAfterLast(':').toInt() }.toList()
    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> = addresses.map { if (!containsSession(it)) throw NoSessionException("Missing session"); loadSession(it) }
    override fun loadPreKey(id: Int) = bytes("prekeys", "$id")?.let { PreKeyRecord(it) } ?: throw InvalidKeyIdException("Missing prekey")
    override fun storePreKey(id: Int, record: PreKeyRecord) = put("prekeys", "$id", record.serialize())
    override fun containsPreKey(id: Int) = section("prekeys").has("$id")
    override fun removePreKey(id: Int) { section("prekeys").remove("$id") }
    override fun loadSignedPreKey(id: Int) = bytes("signed", "$id")?.let { SignedPreKeyRecord(it) } ?: throw InvalidKeyIdException("Missing signed prekey")
    override fun storeSignedPreKey(id: Int, record: SignedPreKeyRecord) = put("signed", "$id", record.serialize())
    override fun containsSignedPreKey(id: Int) = section("signed").has("$id")
    override fun removeSignedPreKey(id: Int) { section("signed").remove("$id") }
    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = section("signed").keys().asSequence().map { loadSignedPreKey(it.toInt()) }.toList()
    override fun loadKyberPreKey(id: Int) = bytes("kyber", "$id")?.let { KyberPreKeyRecord(it) } ?: throw InvalidKeyIdException("Missing Kyber prekey")
    override fun storeKyberPreKey(id: Int, record: KyberPreKeyRecord) = put("kyber", "$id", record.serialize())
    override fun containsKyberPreKey(id: Int) = section("kyber").has("$id")
    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> = section("kyber").keys().asSequence().map { loadKyberPreKey(it.toInt()) }.toList()
    override fun markKyberPreKeyUsed(id: Int, signedId: Int, base: ECPublicKey) {
        // Published Kyber key is explicitly a last-resort key. Persist replay tuples.
        val tuple = "$id:$signedId:${base.serialize().b64()}"
        if (section("usedKyber").has(tuple)) throw ReusedBaseKeyException("Reused base key")
        section("usedKyber").put(tuple, true)
    }
    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) { error("Gruplar desteklenmiyor") }
    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? = null
}
