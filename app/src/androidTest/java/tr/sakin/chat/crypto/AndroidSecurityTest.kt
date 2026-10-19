package tr.sakin.chat.crypto

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidSecurityTest {
    @Test fun keystoreVaultSurvivesReopenAndRejectsTampering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uid = "vault-test-${UUID.randomUUID()}"; val vault = Vault(context, uid)
        try {
            val message = "Özel mesaj gövdesi — diskte açık olmamalı"
            vault.write(JSONObject().put("message", message))
            val file = File(context.noBackupFilesDir, "signal-$uid.bin")
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(message))
            assertEquals(message, Vault(context, uid).read().getString("message"))
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            assertThrows(Exception::class.java) { vault.read() }
        } finally { vault.delete() }
    }
    @Test fun androidNativeSignalAndKeystorePersistenceRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val a = "android-a-${UUID.randomUUID()}"; val b = "android-b-${UUID.randomUUID()}"
        val av = Vault(context, a); val bv = Vault(context, b)
        try {
            val alice = SignalEngine(a, av::read, av::write); val bob = SignalEngine(b, bv::read, bv::write)
            alice.verify(b, bob.publicBundle()); bob.verify(a, alice.publicBundle())
            val e = alice.encrypt("message", "chat", b, bob.publicBundle(), "Gerçek JNI testi")
            val restartedBob = SignalEngine(b, bv::read, bv::write)
            assertEquals("Gerçek JNI testi", restartedBob.decrypt("message", "chat", a, e.getInt("type"), e.getString("ciphertext"), alice.publicBundle()))
        } finally { av.delete(); bv.delete() }
    }
}
