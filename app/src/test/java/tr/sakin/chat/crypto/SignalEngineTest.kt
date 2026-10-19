package tr.sakin.chat.crypto

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SignalEngineTest {
    private class Disk {
        var data = "{}"
        var fail = false
        fun engine(uid: String) = SignalEngine(uid, { JSONObject(data) }, { if (fail) error("Simulated disk failure"); data = it.toString() })
    }
    private fun decrypt(engine: SignalEngine, e: JSONObject, peer: SignalEngine): String = engine.decrypt(
        e.getString("id"), e.getString("chat"), e.getString("sender"), e.getInt("type"), e.getString("ciphertext"), peer.publicBundle())
    @Test fun realSignalRoundTripReplyAndRestart() {
        val a = Disk(); val b = Disk(); var alice = a.engine("alice"); var bob = b.engine("bob")
        alice.verify("bob", bob.publicBundle()); bob.verify("alice", alice.publicBundle())
        assertEquals(alice.safetyNumber("bob", bob.publicBundle()), bob.safetyNumber("alice", alice.publicBundle()))
        val first = alice.encrypt("one", "ab", "bob", bob.publicBundle(), "Merhaba Bob")
        assertFalse(first.toString().contains("Merhaba"))
        assertEquals("Merhaba Bob", decrypt(bob, first, alice))
        val reply = bob.encrypt("two", "ab", "alice", alice.publicBundle(), "Merhaba Alice")
        assertEquals("Merhaba Alice", decrypt(alice, reply, bob))
        alice = a.engine("alice"); bob = b.engine("bob")
        val afterRestart = alice.encrypt("three", "ab", "bob", bob.publicBundle(), "Kalıcı oturum")
        assertEquals("Kalıcı oturum", decrypt(bob, afterRestart, alice))
    }
    @Test fun identityMustBeExplicitlyVerifiedAndChangeBlocksSending() {
        val alice = Disk().engine("alice"); val bob = Disk().engine("bob")
        assertThrows(IllegalStateException::class.java) { alice.encrypt("one", "ab", "bob", bob.publicBundle(), "Secret") }
        alice.verify("bob", bob.publicBundle())
        val replacement = Disk().engine("bob")
        assertFalse(alice.isVerified("bob", replacement.publicBundle()))
        assertThrows(IllegalStateException::class.java) { alice.encrypt("two", "ab", "bob", replacement.publicBundle(), "Secret") }
    }
    @Test fun outboxRetryUsesIdenticalCiphertextAndSurvivesRestart() {
        val disk = Disk(); val alice = disk.engine("alice"); val bob = Disk().engine("bob")
        alice.verify("bob", bob.publicBundle())
        val first = alice.encrypt("same-id", "ab", "bob", bob.publicBundle(), "Secret")
        val restarted = disk.engine("alice")
        val retried = restarted.encrypt("same-id", "ab", "bob", bob.publicBundle(), "Secret")
        assertEquals(first.toString(), retried.toString())
        assertEquals(1, restarted.pending().size)
        restarted.acknowledge("same-id")
        assertTrue(disk.engine("alice").pending().isEmpty())
    }
    @Test fun modifiedCiphertextIsRejectedAndDoesNotAdvanceSession() {
        val alice = Disk().engine("alice"); val bob = Disk().engine("bob")
        alice.verify("bob", bob.publicBundle()); bob.verify("alice", alice.publicBundle())
        val envelope = alice.encrypt("id", "ab", "bob", bob.publicBundle(), "Secret")
        val bytes = envelope.getString("ciphertext").unb64(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        val damaged = JSONObject(envelope.toString()).put("ciphertext", bytes.b64())
        assertThrows(Exception::class.java) { decrypt(bob, damaged, alice) }
        assertEquals("Secret", decrypt(bob, envelope, alice))
    }
    @Test fun contextMismatchIsRejectedWithoutLosingOriginalMessage() {
        val alice = Disk().engine("alice"); val bob = Disk().engine("bob")
        alice.verify("bob", bob.publicBundle()); bob.verify("alice", alice.publicBundle())
        val envelope = alice.encrypt("id", "ab", "bob", bob.publicBundle(), "Secret")
        assertThrows(IllegalStateException::class.java) { bob.decrypt("changed-id", "ab", "alice", envelope.getInt("type"), envelope.getString("ciphertext"), alice.publicBundle()) }
        assertEquals("Secret", decrypt(bob, envelope, alice))
    }
    @Test fun failedLocalCommitDoesNotLoseSendAndDoesNotConsumeReceiveState() {
        val a = Disk(); val b = Disk(); val alice = a.engine("alice"); val bob = b.engine("bob")
        alice.verify("bob", bob.publicBundle()); bob.verify("alice", alice.publicBundle())
        a.fail = true
        assertThrows(IllegalStateException::class.java) { alice.encrypt("id", "ab", "bob", bob.publicBundle(), "Secret") }
        a.fail = false
        assertTrue(alice.pending().isEmpty())
        val envelope = alice.encrypt("id", "ab", "bob", bob.publicBundle(), "Secret")
        b.fail = true
        assertThrows(IllegalStateException::class.java) { decrypt(bob, envelope, alice) }
        b.fail = false
        assertEquals("Secret", decrypt(bob, envelope, alice))
    }
    @Test fun repeatedIncomingMessageIsServedFromAuthenticatedLocalCache() {
        val alice = Disk().engine("alice"); val bob = Disk().engine("bob")
        alice.verify("bob", bob.publicBundle()); bob.verify("alice", alice.publicBundle())
        val e = alice.encrypt("id", "ab", "bob", bob.publicBundle(), "Secret")
        assertEquals("Secret", decrypt(bob,e,alice)); assertEquals("Secret", decrypt(bob,e,alice))
    }
    @Test fun cachedMessageCannotCrossChatBoundaries() {
        val alice = Disk().engine("alice"); val bob = Disk().engine("bob")
        alice.verify("bob", bob.publicBundle()); bob.verify("alice", alice.publicBundle())
        val e = alice.encrypt("id", "ab", "bob", bob.publicBundle(), "Secret")
        decrypt(bob, e, alice)
        assertNull(bob.cached("id", "another-chat"))
        assertNull(bob.cached("id", "ab", "mallory"))
        assertThrows(Exception::class.java) { bob.decrypt("id", "another-chat", "alice", e.getInt("type"), e.getString("ciphertext"), alice.publicBundle()) }
    }
    @Test fun outOfOrderDeliveryAndRatchetReplyWork() {
        val alice = Disk().engine("alice"); val bob = Disk().engine("bob")
        alice.verify("bob", bob.publicBundle()); bob.verify("alice", alice.publicBundle())
        val one = alice.encrypt("one", "ab", "bob", bob.publicBundle(), "One")
        val two = alice.encrypt("two", "ab", "bob", bob.publicBundle(), "Two")
        assertEquals("Two", decrypt(bob, two, alice))
        assertEquals("One", decrypt(bob, one, alice))
        val reply = bob.encrypt("reply", "ab", "alice", alice.publicBundle(), "Reply")
        assertEquals("Reply", decrypt(alice, reply, bob))
        val three = alice.encrypt("three", "ab", "bob", bob.publicBundle(), "Three")
        assertEquals("Three", decrypt(bob, three, alice))
    }
    @Test fun weeklyPrekeyRotationPreservesIdentityAndDelayedFirstContact() {
        val a = Disk(); val b = Disk(); val alice = a.engine("alice"); val bob = b.engine("bob")
        alice.verify("bob", bob.publicBundle()); bob.verify("alice", alice.publicBundle())
        val oldIdentity = bob.publicBundle()["identity"]
        val delayed = alice.encrypt("delayed", "ab", "bob", bob.publicBundle(), "Before rotation")
        val json = JSONObject(b.data)
        val old = org.signal.libsignal.protocol.state.SignedPreKeyRecord(json.getJSONObject("signed").getString("1").unb64())
        val aged = org.signal.libsignal.protocol.state.SignedPreKeyRecord(1, 0, old.keyPair, old.signature)
        json.getJSONObject("signed").put("1", aged.serialize().b64()); b.data = json.toString()
        val rotated = b.engine("bob")
        assertEquals(oldIdentity, rotated.publicBundle()["identity"])
        assertEquals(2L, rotated.publicBundle()["signedId"])
        assertEquals("Before rotation", decrypt(rotated, delayed, alice))
    }
}
