package app.casz.notifybridge.data.local.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_MAX_RETRIES = intPreferencesKey("global_max_retries")
        val KEY_TIMEOUT_SECONDS = intPreferencesKey("global_timeout_seconds")
        val KEY_GLOBAL_VARIABLES = stringPreferencesKey("global_variables_json") // JSON map: API_KEY -> 12345
    }

    // --- Lectura de Preferencias ---
    val maxRetries: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_MAX_RETRIES] ?: 3 // Valor por defecto: 3
    }

    val timeoutSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_TIMEOUT_SECONDS] ?: 10 // Valor por defecto: 10s
    }

    val globalVariablesJson: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_GLOBAL_VARIABLES] ?: "{}" // Diccionario vacío
    }

    // --- Escritura de Preferencias ---
    suspend fun setMaxRetries(retries: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MAX_RETRIES] = retries
        }
    }

    suspend fun setTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TIMEOUT_SECONDS] = seconds
        }
    }

    suspend fun setGlobalVariablesJson(jsonString: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GLOBAL_VARIABLES] = jsonString
        }
    }
}
