package tr.sakin.chat.live

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

// Firebase's exception constructors use Android SDK helpers. Exercise their
// actual implementation on Android rather than returning defaults from stubs.
@RunWith(AndroidJUnit4::class)
class FirestoreDiagnosticsTest {
    private fun issue(code: Code, message: String, operation: DatabaseOperation = DatabaseOperation.CHAT_LIST): DatabaseIssue =
        FirestoreDiagnostics.describe(DatabaseFailure(operation, FirebaseFirestoreException(message, code)))!!

    @Test fun missingIndexKeepsRealCodeAndChatListContext() {
        val result = issue(Code.FAILED_PRECONDITION, "The query requires an index. You can create it here: https://console.firebase.google.com/...")
        assertEquals(DatabaseReason.INDEX_REQUIRED, result.reason)
        assertTrue(result.userMessage.contains("Sohbet listesi"))
        assertTrue(result.userMessage.contains("FAILED_PRECONDITION · CHAT_LIST"))
        assertEquals("operation=CHAT_LIST code=FAILED_PRECONDITION reason=INDEX_REQUIRED", result.logLine)
    }
    @Test fun buildingIndexIsDifferentFromMissingIndex() {
        assertEquals(DatabaseReason.INDEX_BUILDING, issue(Code.FAILED_PRECONDITION, "The index is currently building").reason)
    }
    @Test fun otherPreconditionsAreNotFalselyCalledMissingIndexes() {
        val result = issue(Code.FAILED_PRECONDITION, "The database has an unmet precondition")
        assertEquals(DatabaseReason.PRECONDITION, result.reason)
        assertFalse(result.userMessage.contains("indeksi eksik"))
    }
    @Test fun permissionDeniedIsNeverReportedAsAnIndexError() {
        val result = issue(Code.PERMISSION_DENIED, "Missing permissions; index may exist")
        assertEquals(DatabaseReason.ACCESS_DENIED, result.reason)
        assertTrue(result.userMessage.contains("PERMISSION_DENIED"))
        assertFalse(result.userMessage.contains("indeksi eksik"))
    }
    @Test fun networkQuotaAuthenticationAndQueryErrorsHaveDistinctActions() {
        for ((code, reason) in listOf(Code.UNAVAILABLE to DatabaseReason.NETWORK, Code.DEADLINE_EXCEEDED to DatabaseReason.NETWORK,
            Code.RESOURCE_EXHAUSTED to DatabaseReason.QUOTA, Code.UNAUTHENTICATED to DatabaseReason.AUTH_REQUIRED,
            Code.INVALID_ARGUMENT to DatabaseReason.INVALID_QUERY, Code.OUT_OF_RANGE to DatabaseReason.INVALID_QUERY)) {
            assertEquals(reason, issue(code, "server detail").reason)
        }
    }
    @Test fun everyFailureCodeRemainsVisibleToSupport() {
        for (code in Code.entries.filterNot { it == Code.OK }) {
            val result = issue(code, "server detail", DatabaseOperation.MESSAGE_SEND)
            assertTrue(result.userMessage.contains(code.name))
            assertTrue(result.logLine.contains("operation=MESSAGE_SEND"))
        }
    }
    @Test fun rawServerTextAndPrivateRequestDetailsNeverReachUiOrLogs() {
        val secret = "secret-message user@example.invalid PRIVATE_KEY ciphertext uid:alice path/chats/secret"
        val result = issue(Code.FAILED_PRECONDITION, "The query requires an index. $secret")
        for (value in listOf("secret-message", "user@example.invalid", "PRIVATE_KEY", "ciphertext", "alice", "path/chats/secret")) {
            assertFalse(result.logLine.contains(value))
            assertFalse(result.userMessage.contains(value))
        }
    }
    @Test fun wrappedAndDirectFirestoreErrorsAreBothRecognized() {
        val error = FirebaseFirestoreException("failed", Code.FAILED_PRECONDITION)
        assertEquals(Code.FAILED_PRECONDITION, FirestoreDiagnostics.describe(RuntimeException(error))!!.code)
        assertEquals(DatabaseOperation.GENERAL, FirestoreDiagnostics.describe(error)!!.operation)
        assertNull(FirestoreDiagnostics.describe(IllegalStateException("local validation")))
    }
    @Test fun databaseOperationPreservesTheSdkCodeAndTagsTheRequest() = runBlocking {
        val original = FirebaseFirestoreException("request details", Code.PERMISSION_DENIED)
        try {
            databaseOperation(DatabaseOperation.MESSAGE_READ) { throw original }
            fail("Expected a failure")
        } catch (failure: DatabaseFailure) {
            assertSame(original, failure.cause)
            assertEquals(DatabaseOperation.MESSAGE_READ, failure.operation)
        }
    }
    @Test fun coroutineCancellationIsNotConvertedToAnError() = runBlocking {
        val original = CancellationException("lifecycle stopped")
        try {
            databaseOperation(DatabaseOperation.CHAT_LIST) { throw original }
            fail("Expected cancellation")
        } catch (cancelled: CancellationException) { assertSame(original, cancelled) }
    }
}
