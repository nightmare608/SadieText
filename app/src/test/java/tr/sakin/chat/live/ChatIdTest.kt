package tr.sakin.chat.live

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatIdTest {
    @Test fun matchesRulesPairVector() {
        val expected = "p2:alice:bob"
        assertEquals(expected, ChatTransport.id("alice", "bob"))
        assertEquals(expected, ChatTransport.id("bob", "alice"))
    }
    @Test fun asciiUidOrderingMatchesRules() {
        val expected = "p2:Alice_1:bob-2"
        assertEquals(expected, ChatTransport.id("bob-2", "Alice_1"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDelimiterInjection() {
        ChatTransport.id("alice:bob", "eve")
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsSelfChat() {
        ChatTransport.id("alice", "alice")
    }
}
