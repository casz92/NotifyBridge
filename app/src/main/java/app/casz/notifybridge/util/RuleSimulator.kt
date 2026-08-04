package app.casz.notifybridge.util

import android.content.Context
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.ui.loadGlobalVars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class SimulationResult {
    data class Success(val code: Int, val body: String) : SimulationResult()
    data class Failure(val error: String) : SimulationResult()
}

object RuleSimulator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Resuelve ÚNICAMENTE las variables globales en la plantilla (por ejemplo, {global_API_KEY}),
     * dejando el resto de variables literal (como {not_text}) para la simulación directa del payload.
     */
    fun resolveTemplate(template: String, context: Context): String {
        var resolved = template
        // Inyectar variables globales únicamente
        val globalVars = loadGlobalVars(context)
        for (pair in globalVars) {
            val placeholder = "{global_${pair.first}}"
            resolved = resolved.replace(placeholder, pair.second)
        }
        return resolved
    }

    /**
     * Ejecuta una simulación de envío HTTP y espera por la respuesta.
     */
    suspend fun simulateSend(context: Context, rule: RuleEntity): SimulationResult = withContext(Dispatchers.IO) {
        val tag = "RuleSimulator"
        try {
            val resolvedUrl = resolveTemplate(rule.httpUrl, context)
            val resolvedBody = resolveTemplate(rule.bodyTemplate, context)

            android.util.Log.d(tag, "Iniciando simulación de envío. Método: ${rule.httpMethod}, URL original: ${rule.httpUrl}, URL resuelta: $resolvedUrl")
            android.util.Log.d(tag, "Cuerpo resuelto: $resolvedBody")

            // Cargar y resolver headers
            val headersMap = mutableMapOf<String, String>()
            if (!rule.headersJson.isNullOrBlank()) {
                try {
                    val headersObj = JSONObject(rule.headersJson)
                    for (key in headersObj.keys()) {
                        val headerValue = headersObj.getString(key)
                        val resolvedVal = resolveTemplate(headerValue, context)
                        headersMap[key] = resolvedVal
                        android.util.Log.d(tag, "Header: $key = $resolvedVal")
                    }
                } catch (e: Exception) {
                    android.util.Log.e(tag, "Error resolviendo headers JSON: ${e.message}")
                }
            }

            val httpMethod = rule.httpMethod.uppercase()
            val requestBody = if (httpMethod == "GET" || httpMethod == "HEAD" || httpMethod == "OPTIONS") {
                null
            } else {
                val contentType = headersMap["Content-Type"] ?: "application/json"
                resolvedBody.toRequestBody((contentType + "; charset=utf-8").toMediaType())
            }

            val requestBuilder = Request.Builder()
                .url(resolvedUrl)
                .method(httpMethod, requestBody)

            for ((key, value) in headersMap) {
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()
            android.util.Log.d(tag, "Realizando llamada HTTP...")
            
            val response: Response = client.newCall(request).execute()
            val responseCode = response.code
            val responseBody = response.body?.string() ?: ""

            android.util.Log.d(tag, "Respuesta recibida. Código: $responseCode")
            android.util.Log.d(tag, "Cuerpo de respuesta: $responseBody")

            SimulationResult.Success(responseCode, responseBody)
        } catch (e: IOException) {
            android.util.Log.e(tag, "Error de red en simulación: ${e.message}", e)
            SimulationResult.Failure("Fallo de red / Timeout: ${e.localizedMessage ?: e.message}")
        } catch (e: Exception) {
            android.util.Log.e(tag, "Error inesperado en simulación: ${e.message}", e)
            SimulationResult.Failure("Error inesperado: ${e.localizedMessage ?: e.message}")
        }
    }
}
