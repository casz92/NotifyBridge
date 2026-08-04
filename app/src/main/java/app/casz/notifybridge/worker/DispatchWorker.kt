package app.casz.notifybridge.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.casz.notifybridge.data.local.entity.DispatchEntity
import app.casz.notifybridge.data.local.entity.DispatchStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class DispatchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dispatchId = inputData.getLong("dispatch_id", -1L)
        if (dispatchId == -1L) {
            return Result.failure()
        }

        // 1. Cargar el envío desde Room
        var dispatch = getDispatchFromRoom(dispatchId) ?: return Result.failure()

        // 2. Si ya está CANCELADO, detener ejecución inmediatamente
        if (dispatch.status == DispatchStatus.CANCELLED) {
            return Result.failure()
        }

        // 3. Actualizar Room a "PROCESSING" y asociar el UUID de WorkManager
        val workUuid = id.toString()
        dispatch = dispatch.copy(
            status = DispatchStatus.PROCESSING,
            workId = workUuid,
            attempts = dispatch.attempts + 1
        )
        updateDispatchInRoom(dispatch)

        // 4. Configurar Cliente HTTP con Timeout obtenido de DataStore/Config (por ejemplo, 10s por defecto)
        val timeoutSeconds = getGlobalTimeoutSeconds()
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        // 5. Construir petición HTTP
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = dispatch.payloadBody.toRequestBody(mediaType)
        val requestBuilder = Request.Builder()
            .url(dispatch.targetUrl)
            .method(dispatch.httpMethod, requestBody)

        // Cargar Headers desde el JSON
        try {
            val headersObj = JSONObject(dispatch.headersJson)
            for (key in headersObj.keys()) {
                requestBuilder.addHeader(key, headersObj.getString(key))
            }
        } catch (e: Exception) {
            Log.e("DispatchWorker", "Error parseando headers: ${e.message}")
        }

        val request = requestBuilder.build()

        // 6. Ejecutar Petición
        try {
            val response: Response = client.newCall(request).execute()
            val responseCode = response.code
            val responseBodyString = response.body?.string() ?: ""

            if (responseCode == 200 || responseCode == 201) {
                // Éxito (200 o 201 completada)
                val finalDispatch = dispatch.copy(
                    status = DispatchStatus.SUCCESS,
                    responseCode = responseCode,
                    responseBody = responseBodyString
                )
                updateDispatchInRoom(finalDispatch)
                return Result.success()
            } else if (responseCode == 401) {
                // Fallo definitivo (401) sin reintentos
                val finalDispatch = dispatch.copy(
                    status = DispatchStatus.FAILED,
                    responseCode = responseCode,
                    responseBody = responseBodyString,
                    errorMessage = "Unauthorized (401) - Fallo definitivo sin reintento"
                )
                updateDispatchInRoom(finalDispatch)
                return Result.failure()
            } else {
                // Otro error HTTP, reintentar con plazo ponderado (Result.retry())
                return handleFailure(dispatch, "HTTP Error: $responseCode", responseCode, responseBodyString)
            }
        } catch (e: IOException) {
            // Error de Red/Timeout
            return handleFailure(dispatch, "Network Failure: ${e.message}", null, null)
        }
    }

    private suspend fun handleFailure(
        dispatch: DispatchEntity,
        errorMessage: String,
        responseCode: Int?,
        responseBody: String?
    ): Result {
        val nextAttempt = dispatch.attempts
        val maxRetries = dispatch.maxRetries

        if (nextAttempt >= maxRetries) {
            // Se alcanzó el límite de reintentos
            val failedDispatch = dispatch.copy(
                status = DispatchStatus.FAILED,
                errorMessage = errorMessage,
                responseCode = responseCode,
                responseBody = responseBody
            )
            updateDispatchInRoom(failedDispatch)
            return Result.failure()
        } else {
            // Se intentará de nuevo (WorkManager aplicará el Backoff configurado)
            val retryDispatch = dispatch.copy(
                status = DispatchStatus.PENDING,
                errorMessage = "$errorMessage (Reintento en cola)",
                responseCode = responseCode
            )
            updateDispatchInRoom(retryDispatch)
            return Result.retry()
        }
    }

    // --- Simulación de accesos a base de datos y configuración ---
    private suspend fun getDispatchFromRoom(id: Long): DispatchEntity? {
        val dispatches = app.casz.notifybridge.ui.loadDispatches(applicationContext)
        return dispatches.firstOrNull { it.id == id }
    }

    private suspend fun updateDispatchInRoom(entity: DispatchEntity) {
        val dispatches = app.casz.notifybridge.ui.loadDispatches(applicationContext).toMutableList()
        val index = dispatches.indexOfFirst { it.id == entity.id }
        if (index != -1) {
            dispatches[index] = entity
            app.casz.notifybridge.ui.saveDispatches(applicationContext, dispatches)
        }
        Log.d("DispatchWorker", "Actualizando Room a estado: ${entity.status} (Intento: ${entity.attempts})")
    }

    private suspend fun getGlobalTimeoutSeconds(): Long {
        val appPrefs = app.casz.notifybridge.data.local.pref.AppPreferences(applicationContext)
        return appPrefs.timeoutSeconds.first().toLong()
    }
}
