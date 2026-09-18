package app.casz.notifybridge.data.repository

import android.content.Context
import org.json.JSONObject

private const val PREFS_NAME = "notifybridge_prefs"
private const val KEY_GLOBAL_VARS_JSON = "global_variables_map_json"

object GlobalVarsRepository {

    fun load(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_GLOBAL_VARS_JSON, "{}") ?: "{}"
        val list = mutableListOf<Pair<String, String>>()
        try {
            val jsonObj = JSONObject(jsonString)
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObj.optString(key, "")
                if (key.isNotBlank()) {
                    list.add(key to value)
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun save(context: Context, vars: List<Pair<String, String>>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonObj = JSONObject()
        vars.forEach { (key, value) ->
            if (key.isNotBlank()) {
                jsonObj.put(key, value)
            }
        }
        prefs.edit().putString(KEY_GLOBAL_VARS_JSON, jsonObj.toString()).apply()
    }
}
