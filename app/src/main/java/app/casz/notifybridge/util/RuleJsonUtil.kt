package app.casz.notifybridge.util

import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.data.local.entity.RuleSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Utilidad modular para la serialización y deserialización de reglas e inyección de variables globales en JSON.
 */
object RuleJsonUtil {

    private val GLOBAL_VAR_REGEX = Pattern.compile("\\{global_([A-Za-z0-9_]+)\\}")

    /**
     * Serializa una lista de reglas a JSON formateado.
     * Escanea las variables globales usadas en cabeceras y cuerpo, inyectándolas en el nodo "_vars".
     * Convierte las cabeceras HTTP en un array de objetos con formato [{"header": "Key", "value": "Val"}].
     */
    private const val MIN_SUPPORTED_VERSION = 1
    private const val CURRENT_VERSION = 1

    /**
     * Serializa una lista de reglas a JSON formateado con control de versión.
     * Escanea las variables globales usadas en cabeceras y cuerpo, inyectándolas en el nodo "_vars".
     * Convierte las cabeceras HTTP en un array de objetos con formato [{"header": "Key", "value": "Val"}].
     */
    fun exportRulesToJson(
        rules: List<RuleEntity>,
        globalVars: List<Pair<String, String>>
    ): String {
        val rootObj = JSONObject()
        rootObj.put("version", CURRENT_VERSION)

        val jsonArray = JSONArray()

        for (rule in rules) {
            val ruleObj = JSONObject()
            ruleObj.put("name", rule.name)
            ruleObj.put("source", rule.source.name)
            ruleObj.put("appPackageNames", rule.appPackageNames ?: JSONObject.NULL)
            ruleObj.put("regexPattern", rule.regexPattern)
            ruleObj.put("regexMatchFields", rule.regexMatchFields)
            ruleObj.put("httpUrl", rule.httpUrl)
            ruleObj.put("httpMethod", rule.httpMethod)

            // Convert headersJson string -> "headers" JSONArray of {"header": "Key", "value": "Value"}
            val headersArray = JSONArray()
            if (!rule.headersJson.isNullOrBlank()) {
                try {
                    val headersMapObj = JSONObject(rule.headersJson)
                    val keys = headersMapObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = headersMapObj.optString(key, "")
                        val headerItem = JSONObject().apply {
                            put("header", key)
                            put("value", value)
                        }
                        headersArray.put(headerItem)
                    }
                } catch (_: Exception) {}
            }
            ruleObj.put("headers", headersArray)

            ruleObj.put("bodyTemplate", rule.bodyTemplate)

            // Scan for used global variables in headers and bodyTemplate
            val usedVarNames = mutableSetOf<String>()
            findGlobalVarNames(rule.headersJson, usedVarNames)
            findGlobalVarNames(rule.bodyTemplate, usedVarNames)

            val varsArray = JSONArray()
            for (varName in usedVarNames) {
                val matchingPair = globalVars.firstOrNull { it.first.equals(varName, ignoreCase = true) }
                if (matchingPair != null) {
                    val varObj = JSONObject().apply {
                        put("name", matchingPair.first)
                        put("value", matchingPair.second)
                    }
                    varsArray.put(varObj)
                }
            }
            ruleObj.put("_vars", varsArray)

            jsonArray.put(ruleObj)
        }

        rootObj.put("rules", jsonArray)
        return rootObj.toString(2)
    }

    /**
     * Deserializa un string JSON a un par que contiene:
     * 1. Lista de objetos RuleEntity importados.
     * 2. Lista de Variables Globales extraídas del nodo "_vars".
     * Soporta tanto el formato legacy (JSONArray raíz, versión implícita 1) como el nuevo formato (JSONObject raíz con "version").
     */
    fun importRulesFromJson(
        jsonContent: String,
        startingId: Long = 1L
    ): Pair<List<RuleEntity>, List<Pair<String, String>>> {
        val importedRules = mutableListOf<RuleEntity>()
        val importedGlobalVars = mutableListOf<Pair<String, String>>()
        var currentId = startingId

        if (jsonContent.isBlank()) {
            return Pair(importedRules, importedGlobalVars)
        }

        val trimmed = jsonContent.trim()
        val jsonArray: JSONArray
        
        if (trimmed.startsWith("{")) {
            val rootObj = JSONObject(trimmed)
            val version = rootObj.optInt("version", 1)
            if (version < MIN_SUPPORTED_VERSION) {
                throw Exception("La versión del archivo importado ($version) es menor que la mínima soportada ($MIN_SUPPORTED_VERSION)")
            }
            jsonArray = rootObj.optJSONArray("rules") ?: JSONArray()
        } else {
            // Formato legacy (JSONArray raíz, versión implícita 1)
            if (MIN_SUPPORTED_VERSION > 1) {
                throw Exception("El formato heredado (sin versión) ya no está soportado. Versión mínima requerida: $MIN_SUPPORTED_VERSION")
            }
            jsonArray = JSONArray(trimmed)
        }

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)

            val name = obj.optString("name", "regla_importada")
            val sourceStr = obj.optString("source", "APP")
            val source = try { RuleSource.valueOf(sourceStr) } catch (_: Exception) { RuleSource.APP }
            val appPkg: String? = if (obj.isNull("appPackageNames")) null else obj.optString("appPackageNames")
            val regex = obj.optString("regexPattern", ".*")
            val regexFields = obj.optString("regexMatchFields", "")
            val url = obj.optString("httpUrl", "https://")
            val method = obj.optString("httpMethod", "POST")

            // Parse headers array [{"header": "Key", "value": "Value"}]
            val headersMap = mutableMapOf<String, String>()
            if (obj.has("headers")) {
                val headersArr = obj.optJSONArray("headers")
                if (headersArr != null) {
                    for (j in 0 until headersArr.length()) {
                        val hObj = headersArr.optJSONObject(j)
                        if (hObj != null) {
                            val hKey = hObj.optString("header", hObj.optString("key", "")).trim()
                            val hVal = hObj.optString("value", "").trim()
                            if (hKey.isNotBlank()) {
                                headersMap[hKey] = hVal
                            }
                        }
                    }
                }
            } else if (obj.has("headersJson")) {
                // Fallback for legacy headersJson format
                try {
                    val legacyObj = JSONObject(obj.optString("headersJson", "{}"))
                    val keys = legacyObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        headersMap[k] = legacyObj.optString(k, "")
                    }
                } catch (_: Exception) {}
            }

            val headersJson = JSONObject(headersMap as Map<*, *>).toString()
            val bodyTemplate = obj.optString("bodyTemplate", "")

            // Parse _vars array [{"name": "varName", "value": "varVal"}]
            if (obj.has("_vars")) {
                val varsArr = obj.optJSONArray("_vars")
                if (varsArr != null) {
                    for (j in 0 until varsArr.length()) {
                        val vObj = varsArr.optJSONObject(j)
                        if (vObj != null) {
                            val vName = vObj.optString("name", "").trim().uppercase().replace(" ", "_")
                            val vValue = vObj.optString("value", "").trim()
                            if (vName.isNotBlank()) {
                                val idx = importedGlobalVars.indexOfFirst { it.first.equals(vName, ignoreCase = true) }
                                if (idx != -1) {
                                    importedGlobalVars[idx] = vName to vValue
                                } else {
                                    importedGlobalVars.add(vName to vValue)
                                }
                            }
                        }
                    }
                }
            }

            val rule = RuleEntity(
                id = currentId++,
                name = name,
                source = source,
                appPackageNames = appPkg,
                regexPattern = regex,
                regexMatchFields = regexFields,
                httpUrl = url,
                httpMethod = method,
                headersJson = headersJson,
                bodyTemplate = bodyTemplate
            )
            importedRules.add(rule)
        }

        return Pair(importedRules, importedGlobalVars)
    }

    private fun findGlobalVarNames(text: String?, resultSet: MutableSet<String>) {
        if (text.isNullOrBlank()) return
        val matcher = GLOBAL_VAR_REGEX.matcher(text)
        while (matcher.find()) {
            val varName = matcher.group(1)
            if (!varName.isNullOrBlank()) {
                resultSet.add(varName)
            }
        }
    }
}
