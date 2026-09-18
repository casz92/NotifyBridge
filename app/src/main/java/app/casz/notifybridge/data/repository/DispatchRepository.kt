package app.casz.notifybridge.data.repository

import android.content.Context
import app.casz.notifybridge.data.local.entity.DispatchEntity
import app.casz.notifybridge.data.local.entity.DispatchStatus
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "notifybridge_prefs"
private const val KEY_DISPATCHES_LIST_JSON = "saved_dispatches_list_json"

object DispatchRepository {

    fun load(context: Context): List<DispatchEntity> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_DISPATCHES_LIST_JSON, "[]") ?: "[]"
        val list = mutableListOf<DispatchEntity>()
        try {
            val jsonArr = JSONArray(jsonString)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val entity = DispatchEntity(
                    id = obj.optLong("id", 0L),
                    workId = if (obj.has("workId") && !obj.isNull("workId")) obj.optString("workId") else null,
                    ruleId = obj.optLong("ruleId", 0L),
                    triggeredBy = obj.optString("triggeredBy", ""),
                    targetUrl = obj.optString("targetUrl", ""),
                    httpMethod = obj.optString("httpMethod", "POST"),
                    headersJson = obj.optString("headersJson", "{}"),
                    payloadBody = obj.optString("payloadBody", ""),
                    status = try { DispatchStatus.valueOf(obj.optString("status", "PENDING")) } catch (_: Exception) { DispatchStatus.PENDING },
                    attempts = obj.optInt("attempts", 0),
                    maxRetries = obj.optInt("maxRetries", 3),
                    responseCode = if (obj.has("responseCode") && !obj.isNull("responseCode")) obj.optInt("responseCode") else null,
                    responseBody = if (obj.has("responseBody") && !obj.isNull("responseBody")) obj.optString("responseBody") else null,
                    errorMessage = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) obj.optString("errorMessage") else null,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
                list.add(entity)
            }
        } catch (_: Exception) {}
        return list
    }

    fun save(context: Context, dispatches: List<DispatchEntity>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArr = JSONArray()
        for (item in dispatches) {
            val obj = JSONObject().apply {
                put("id", item.id)
                if (item.workId != null) put("workId", item.workId)
                put("ruleId", item.ruleId)
                put("triggeredBy", item.triggeredBy)
                put("targetUrl", item.targetUrl)
                put("httpMethod", item.httpMethod)
                put("headersJson", item.headersJson)
                put("payloadBody", item.payloadBody)
                put("status", item.status.name)
                put("timestamp", item.timestamp)
                put("attempts", item.attempts)
                put("maxRetries", item.maxRetries)
                if (item.responseCode != null) put("responseCode", item.responseCode)
                if (item.responseBody != null) put("responseBody", item.responseBody)
                if (item.errorMessage != null) put("errorMessage", item.errorMessage)
            }
            jsonArr.put(obj)
        }
        prefs.edit().putString(KEY_DISPATCHES_LIST_JSON, jsonArr.toString()).apply()
    }
}
