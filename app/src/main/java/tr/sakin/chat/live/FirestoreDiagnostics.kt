package tr.sakin.chat.live

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import java.util.Locale

enum class DatabaseOperation {
    GENERAL, CHAT_LIST, CHAT_PAGE, CHAT_OPEN, MESSAGE_LIST, MESSAGE_PAGE,
    MESSAGE_SEND, MESSAGE_READ, MESSAGE_DELETE, ACCOUNT_DELETE
}

class DatabaseFailure(val operation: DatabaseOperation, cause: FirebaseFirestoreException) :
    Exception("Firestore operation ${operation.name} failed (${cause.code.name})", cause)

suspend fun <T> databaseOperation(operation: DatabaseOperation, block: suspend () -> T): T =
    try { block() }
    catch (error: CancellationException) { throw error }
    catch (error: FirebaseFirestoreException) { throw DatabaseFailure(operation, error) }

enum class DatabaseReason { INDEX_REQUIRED, INDEX_BUILDING, ACCESS_DENIED, AUTH_REQUIRED, PRECONDITION,
    INVALID_QUERY, QUOTA, NETWORK, RETRY, OTHER }

data class DatabaseIssue(
    val code: FirebaseFirestoreException.Code,
    val operation: DatabaseOperation,
    val reason: DatabaseReason,
    val explanation: String
) {
    // Only enum constants are logged. Never log the exception, query, UID,
    // document path, email, message text, ciphertext or encryption keys.
    val logLine: String get() = "operation=${operation.name} code=${code.name} reason=${reason.name}"
    val userMessage: String get() = "$explanation\nDestek kodu: ${code.name} · ${operation.name}"
}

object FirestoreDiagnostics {
    fun describe(error: Throwable): DatabaseIssue? {
        var current: Throwable? = error
        var operation = DatabaseOperation.GENERAL
        repeat(8) {
            val value = current ?: return null
            if (value is DatabaseFailure) operation = value.operation
            if (value is FirebaseFirestoreException) return describe(value, operation)
            current = value.cause
        }
        return null
    }

    private fun describe(error: FirebaseFirestoreException, operation: DatabaseOperation): DatabaseIssue {
        // The server's text is inspected in memory for classification only.
        // It is never copied to UI/logs because it may contain request details.
        val detail = error.message.orEmpty().lowercase(Locale.ROOT)
        val (reason, text) = when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> DatabaseReason.ACCESS_DENIED to
                "Bu işlem için erişim izni yok. E-postanı doğrula; sorun sürerse uygulama yöneticisine bildir."
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> DatabaseReason.AUTH_REQUIRED to
                "Oturum doğrulanamadı. Çıkış yapıp yeniden giriş yap."
            FirebaseFirestoreException.Code.FAILED_PRECONDITION -> when {
                "index" in detail && ("building" in detail || "not ready" in detail) ->
                    DatabaseReason.INDEX_BUILDING to "Sunucu indeksi hazırlanıyor. Birkaç dakika sonra Yeniden bağlan'a bas."
                "index" in detail && ("requires an index" in detail || "require an index" in detail || "missing index" in detail) ->
                    DatabaseReason.INDEX_REQUIRED to if (operation in listOf(DatabaseOperation.CHAT_LIST, DatabaseOperation.CHAT_PAGE))
                        "Sohbet listesi için sunucu indeksi eksik. Uygulama yöneticisinin sohbet indeksini oluşturması gerekiyor."
                    else "Bu işlem için sunucu indeksi eksik. Uygulama yöneticisinin kurulumu tamamlaması gerekiyor."
                else -> DatabaseReason.PRECONDITION to
                    "Sunucunun işlem koşulları hazır değil. Destek kodunu uygulama yöneticisine bildir."
            }
            FirebaseFirestoreException.Code.INVALID_ARGUMENT, FirebaseFirestoreException.Code.OUT_OF_RANGE ->
                DatabaseReason.INVALID_QUERY to "Sorgu veya gönderilen veri sunucu tarafından kabul edilmedi. Destek kodunu uygulama yöneticisine bildir."
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> DatabaseReason.QUOTA to
                "Hizmet kotası veya istek sınırı aşıldı. Bir süre sonra yeniden dene."
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                DatabaseReason.NETWORK to "Sunucuya ulaşılamıyor. İnternetini kontrol et; bekleyen mesajlar cihazda korunur."
            FirebaseFirestoreException.Code.ABORTED, FirebaseFirestoreException.Code.CANCELLED -> DatabaseReason.RETRY to
                "İşlem kesintiye uğradı. Yeniden deneyebilirsin."
            else -> DatabaseReason.OTHER to "Veritabanı isteği başarısız oldu. Destek kodunu uygulama yöneticisine bildir."
        }
        return DatabaseIssue(error.code, operation, reason, text)
    }
}
