package tr.sakin.chat.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local persistence only. Message transport uses libsignal, never this cipher. */
class Vault(context: Context, private val uid: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "signal-$uid.bin"))
    private val alias = "sakin-signal-$uid"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) { "Yerel anahtar kayıp. Uygulamayı yeniden kurma kurtarma akışını kullanın." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    fun read(): JSONObject {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return JSONObject()
        val bytes = file.readFully()
        require(bytes.size > 29 && bytes[0].toInt() == 1) { "Yerel kasa biçimi geçersiz." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(uid.toByteArray(Charsets.UTF_8))
        return JSONObject(cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8))
    }
    fun write(json: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(uid.toByteArray(Charsets.UTF_8))
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
    fun delete() {
        file.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}
