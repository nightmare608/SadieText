package tr.sakin.chat.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException

private val Context.settings by preferencesDataStore("appearance")
enum class ThemeMode { SYSTEM, LIGHT, DARK }
class ThemeStore(private val context: Context) {
    private val key = stringPreferencesKey("theme")
    val mode = context.settings.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { pref -> ThemeMode.entries.firstOrNull { it.name == pref[key] } ?: ThemeMode.SYSTEM }
    suspend fun set(mode: ThemeMode) { context.settings.edit { it[key] = mode.name } }
}
