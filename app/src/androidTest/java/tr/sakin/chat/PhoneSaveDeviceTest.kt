package tr.sakin.chat

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tr.sakin.chat.games.phone.PhoneEngine
import tr.sakin.chat.games.phone.PhoneViewModel
import tr.sakin.chat.games.phone.Blueprint
import java.security.MessageDigest
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhoneSaveDeviceTest {
    @Test fun atomicSaveReopensAndAccountsStaySeparate() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val scope = "save-test-${UUID.randomUUID()}"
        val other = "$scope-other"
        try {
            val first = PhoneViewModel(app,scope)
            await { !first.state.value.loading }
            first.newCompany("Kayıt Testi")
            await { !first.state.value.saving }
            first.change { PhoneEngine.launch(it,Blueprint(),20,5990) }
            await { !first.state.value.saving }
            first.change(PhoneEngine::nextWeek)
            await { !first.state.value.saving }
            assertNull(first.state.value.error)
            val reopened = PhoneViewModel(app,scope)
            val separate = PhoneViewModel(app,other)
            await { !reopened.state.value.loading && !separate.state.value.loading }
            assertEquals(first.state.value.company,reopened.state.value.company)
            assertEquals("",separate.state.value.company!!.name)
            assertEquals(2,reopened.state.value.company!!.week)
        } finally {
            listOf(scope,other).forEach { id ->
                val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
                File(app.filesDir,"phone-company-$hash.json").delete()
            }
        }
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while(!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("Timed out waiting for atomic save/load",condition())
    }
}
