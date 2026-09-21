package app.casz.notifybridge.data.repository

import android.content.Context
import android.util.Log
import app.casz.notifybridge.data.local.entity.RuleSource
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.util.RuleJsonUtil
import org.json.JSONObject

private const val PREFS_NAME = "notifybridge_prefs"
private const val KEY_RULES_LIST_JSON = "saved_rules_list_json"
private const val KEY_INITIAL_RULES_SEEDED = "initial_rules_seeded"

private const val DRAFT_PREFS_NAME = "notifybridge_draft_prefs"
private const val KEY_ACTIVE_DRAFT_JSON = "active_rule_draft_json"

object RulesRepository {

    fun getDefaultRules(): List<RuleEntity> {
        return listOf(
            RuleEntity(
                id = 1L,
                name = "Nequi - Notificación de Pago",
                source = RuleSource.APP,
                appPackageNames = "com.nequi.MobileApp",
                regexPattern = "(?i)(te envi[oó]|te enviaron|te acaba(n)? de enviar|recibiste un pago|te transfirieron|pago recibido)",
                regexMatchFields = "title,text",
                httpUrl = "https://midominio.com/api/pagos",
                httpMethod = "POST",
                headersJson = "{\"Content-Type\":\"application/json\"}",
                bodyTemplate = "{\n  \"bank\": \"Nequi\",\n  \"type\": \"{not_type}\",\n  \"title\": \"{not_title}\",\n  \"message\": \"{not_text}\",\n  \"package_name\": \"{package_name}\",\n  \"timestamp\": \"{timestamp}\",\n  \"device_uuid\": \"{device_uuid}\"\n}",
                enabled = true,
                regexBlocksJson = ""
            ),
            RuleEntity(
                id = 2L,
                name = "Bancolombia - Notificación de Pago",
                source = RuleSource.APP,
                appPackageNames = "com.todo1.mobile, co.com.tcs.bancolombia.bancaalamano",
                regexPattern = "(?i)(transferencia recibida|recepci[oó]n de transferencia|le informamos transferencia|recibiste una transferencia|pago recibido|abono)",
                regexMatchFields = "title,text",
                httpUrl = "https://midominio.com/api/pagos",
                httpMethod = "POST",
                headersJson = "{\"Content-Type\":\"application/json\"}",
                bodyTemplate = "{\n  \"bank\": \"Bancolombia\",\n  \"type\": \"{not_type}\",\n  \"title\": \"{not_title}\",\n  \"message\": \"{not_text}\",\n  \"package_name\": \"{package_name}\",\n  \"timestamp\": \"{timestamp}\",\n  \"device_uuid\": \"{device_uuid}\"\n}",
                enabled = true,
                regexBlocksJson = ""
            ),
            RuleEntity(
                id = 3L,
                name = "Nequi - SMS de Pago",
                source = RuleSource.SMS,
                appPackageNames = null,
                regexPattern = "(?i)(nequi|85954)?.*?(te envi[oó]|te enviaron|te acaba(n)? de enviar|transfiri[oó]|recibiste|plata)",
                regexMatchFields = "sender,body",
                httpUrl = "https://midominio.com/api/pagos",
                httpMethod = "POST",
                headersJson = "{\"Content-Type\":\"application/json\"}",
                bodyTemplate = "{\n  \"bank\": \"Nequi\",\n  \"type\": \"{not_type}\",\n  \"sender\": \"{sms_sender}\",\n  \"message\": \"{sms_text}\",\n  \"timestamp\": \"{timestamp}\",\n  \"device_uuid\": \"{device_uuid}\"\n}",
                enabled = true,
                regexBlocksJson = ""
            ),
            RuleEntity(
                id = 4L,
                name = "Bancolombia - SMS de Pago",
                source = RuleSource.SMS,
                appPackageNames = null,
                regexPattern = "(?i)(bancolombia|85432)?.*?(transferencia|recepci[oó]n|abono|recibi[oó]|pago)",
                regexMatchFields = "sender,body",
                httpUrl = "https://midominio.com/api/pagos",
                httpMethod = "POST",
                headersJson = "{\"Content-Type\":\"application/json\"}",
                bodyTemplate = "{\n  \"bank\": \"Bancolombia\",\n  \"type\": \"{not_type}\",\n  \"sender\": \"{sms_sender}\",\n  \"message\": \"{sms_text}\",\n  \"timestamp\": \"{timestamp}\",\n  \"device_uuid\": \"{device_uuid}\"\n}",
                enabled = true,
                regexBlocksJson = ""
            )
        )
    }

    fun load(context: Context): List<RuleEntity> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasSeeded = prefs.getBoolean(KEY_INITIAL_RULES_SEEDED, false)
        val rawJson = prefs.getString(KEY_RULES_LIST_JSON, null)

        if (!hasSeeded && (rawJson == null || rawJson.trim() == "{\"version\":1,\"rules\":[]}")) {
            val defaults = getDefaultRules()
            save(context, defaults)
            prefs.edit().putBoolean(KEY_INITIAL_RULES_SEEDED, true).apply()
            return defaults
        }

        if (!hasSeeded) {
            prefs.edit().putBoolean(KEY_INITIAL_RULES_SEEDED, true).apply()
        }

        var jsonString = rawJson ?: "{\"version\":1,\"rules\":[]}"

        // Migración automática de campos de plantillas en español a inglés si existían
        if (jsonString.contains("\"banco\":") || jsonString.contains("\"remitente\":") || jsonString.contains("\"dispositivo\":")) {
            jsonString = jsonString
                .replace("\"banco\":", "\"bank\":")
                .replace("\"tipo\": \"notificacion\"", "\"type\": \"notification\"")
                .replace("\"tipo\": \"sms\"", "\"type\": \"sms\"")
                .replace("\"tipo\":", "\"type\":")
                .replace("\"titulo\":", "\"title\":")
                .replace("\"remitente\":", "\"sender\":")
                .replace("\"mensaje\":", "\"message\":")
                .replace("\"paquete\":", "\"package_name\":")
                .replace("\"dispositivo\":", "\"device_uuid\":")
            prefs.edit().putString(KEY_RULES_LIST_JSON, jsonString).apply()
        }

        return try {
            val (rules, _) = RuleJsonUtil.importRulesFromJson(jsonString, 1L)
            rules
        } catch (e: Exception) {
            Log.e("RulesRepository", "Error importando reglas, intentando migrar legacy: ${e.message}")
            try {
                val legacyArray = org.json.JSONArray(jsonString)
                val migratedObj = org.json.JSONObject().apply {
                    put("version", 1)
                    put("rules", legacyArray)
                }
                val (rules, _) = RuleJsonUtil.importRulesFromJson(migratedObj.toString(), 1L)
                save(context, rules)
                rules
            } catch (ex: Exception) {
                Log.e("RulesRepository", "Fallo total al cargar reglas: ${ex.message}")
                emptyList()
            }
        }
    }

    fun resetToDefaultRules(context: Context): List<RuleEntity> {
        val defaults = getDefaultRules()
        save(context, defaults)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_INITIAL_RULES_SEEDED, true).apply()
        return defaults
    }

    fun save(context: Context, rules: List<RuleEntity>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = RuleJsonUtil.exportRulesToJson(rules, emptyList())
        prefs.edit().putString(KEY_RULES_LIST_JSON, jsonString).apply()
    }

    // --- Draft helpers ---

    fun loadDraft(context: Context): JSONObject? {
        val prefs = context.getSharedPreferences(DRAFT_PREFS_NAME, Context.MODE_PRIVATE)
        val str = prefs.getString(KEY_ACTIVE_DRAFT_JSON, null) ?: return null
        return try { JSONObject(str) } catch (_: Exception) { null }
    }

    fun saveDraft(context: Context, jsonString: String) {
        val prefs = context.getSharedPreferences(DRAFT_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_DRAFT_JSON, jsonString).apply()
    }

    fun clearDraft(context: Context) {
        val prefs = context.getSharedPreferences(DRAFT_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ACTIVE_DRAFT_JSON).apply()
    }
}
