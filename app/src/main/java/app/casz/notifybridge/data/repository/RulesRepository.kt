package app.casz.notifybridge.data.repository

import android.content.Context
import android.util.Log
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.util.RuleJsonUtil
import org.json.JSONObject

private const val PREFS_NAME = "notifybridge_prefs"
private const val KEY_RULES_LIST_JSON = "saved_rules_list_json"

private const val DRAFT_PREFS_NAME = "notifybridge_draft_prefs"
private const val KEY_ACTIVE_DRAFT_JSON = "active_rule_draft_json"

object RulesRepository {

    fun load(context: Context): List<RuleEntity> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_RULES_LIST_JSON, "{\"version\":1,\"rules\":[]}") ?: "{\"version\":1,\"rules\":[]}"
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
