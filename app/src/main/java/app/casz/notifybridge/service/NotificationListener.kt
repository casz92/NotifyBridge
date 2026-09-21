package app.casz.notifybridge.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.casz.notifybridge.data.local.entity.DispatchEntity
import app.casz.notifybridge.data.local.entity.DispatchStatus
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.data.local.entity.RuleSource
import app.casz.notifybridge.data.local.entity.matches
import app.casz.notifybridge.worker.DispatchWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID
import java.util.regex.Pattern

class NotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE, "")
        val text = extras.getCharSequence(Notification.EXTRA_TEXT, "").toString()
        val notId = sbn.id.toString()
        val systemTime = System.currentTimeMillis().toString()

        // 1. Evitar procesar exactamente el mismo contenido para la misma notificación si ya se procesó
        if (isNotificationDuplicate(packageName, notId, title, text)) {
            Log.d("NotificationListener", "Notificación duplicada ignorada: pkg=$packageName, id=$notId, title=$title")
            return
        }

        // 2. Extraer sólo el nuevo fragmento de texto si es una actualización de chat (WhatsApp, Telegram, etc.)
        val processedText = getNewTextForChat(packageName, title, text)
        if (processedText.isBlank()) {
            Log.d("NotificationListener", "No hay contenido nuevo en la actualización de la notificación")
            return
        }

        serviceScope.launch {
            // 3. Obtener reglas activas para apps e IMAP
            val rules = getRulesFromDatabase()

            for (rule in rules) {
                if (!rule.enabled) continue
                if (rule.source == RuleSource.APP) {
                    val packages = rule.appPackageNames?.split(",")?.map { it.trim() } ?: emptyList()
                    if (packages.isEmpty() || packages.contains(packageName) || packages.contains("*")) {
                        val isMatch = rule.matches(mapOf("title" to title, "text" to processedText))
                        if (isMatch) {
                            val finalPayload = resolveVariables(
                                template = rule.bodyTemplate,
                                title = title,
                                id = notId,
                                text = processedText,
                                systemTime = systemTime,
                                packageName = packageName
                            )
                            val parsedUrl = rule.httpUrl.trim().toHttpUrlOrNull()
                            val isUrlValid = parsedUrl != null && parsedUrl.host.isNotBlank() && rule.httpUrl.trim() != "https://" && rule.httpUrl.trim() != "http://"
                            if (!isUrlValid) {
                                saveFailedDispatch(rule, finalPayload, "App: $packageName", "URL de destino no configurada o inválida ('${rule.httpUrl}')")
                            } else {
                                val dispatchId = savePendingDispatch(rule, finalPayload, "App: $packageName")
                                enqueueWork(dispatchId)
                            }
                        }
                    }
                } else if (rule.source == RuleSource.IMAP) {
                    if (packageName == "com.google.android.gm") {
                        Log.d("NotificationListener", "Gmail notif recibida — disparando ImapFetchWorker.")
                        enqueueImapFetchWork(rule.id)
                    }
                }
            }

            markNotificationProcessed(packageName, notId, title, text)
            updateChatHistory(packageName, title, text)
        }
    }

    private fun isNotificationDuplicate(packageName: String, notId: String, title: String, text: String): Boolean {
        val prefs = applicationContext.getSharedPreferences("notification_dedup_prefs", Context.MODE_PRIVATE)
        val key = "${packageName}_${notId}_${title}_${text.hashCode()}"
        return prefs.contains(key)
    }

    private fun markNotificationProcessed(packageName: String, notId: String, title: String, text: String) {
        val prefs = applicationContext.getSharedPreferences("notification_dedup_prefs", Context.MODE_PRIVATE)
        val key = "${packageName}_${notId}_${title}_${text.hashCode()}"
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
    }

    private fun getNewTextForChat(packageName: String, title: String, currentText: String): String {
        if (title.isBlank() || currentText.isBlank()) return currentText
        val prefs = applicationContext.getSharedPreferences("notification_chat_history", Context.MODE_PRIVATE)
        val key = "${packageName}_${title}"
        val lastText = prefs.getString(key, "") ?: ""

        if (lastText.isNotEmpty() && currentText.startsWith(lastText)) {
            val newPart = currentText.substring(lastText.length)
            val trimmedNewPart = newPart.trimStart('\n', '\r', ' ')
            if (trimmedNewPart.isNotEmpty()) {
                return trimmedNewPart
            } else {
                return "" // No hay contenido nuevo real
            }
        }
        return currentText
    }

    private fun updateChatHistory(packageName: String, title: String, currentText: String) {
        if (title.isBlank() || currentText.isBlank()) return
        val prefs = applicationContext.getSharedPreferences("notification_chat_history", Context.MODE_PRIVATE)
        val key = "${packageName}_${title}"
        prefs.edit().putString(key, currentText).apply()
    }

    private fun enqueueImapFetchWork(ruleId: Long) {
        val workRequest = OneTimeWorkRequest.Builder(app.casz.notifybridge.worker.ImapFetchWorker::class.java)
            .setInputData(
                Data.Builder()
                    .putLong("rule_id", ruleId)
                    .build()
            )
            .build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        Log.d("NotificationListener", "ImapFetchWorker encolado con ID de regla: $ruleId")
    }

    private fun matchesPattern(text: String, patternStr: String): Boolean {
        return try {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            pattern.matcher(text).find()
        } catch (e: Exception) {
            Log.e("NotificationListener", "Pattern error: ${e.message}")
            false
        }
    }

    private fun resolveVariables(
        template: String,
        title: String,
        id: String,
        text: String,
        systemTime: String,
        packageName: String
    ): String {
        var resolved = template
            .replace("{not_title}", title)
            .replace("{sms_sender}", title)
            .replace("{not_id}", id)
            .replace("{not_text}", text)
            .replace("{sms_text}", text)
            .replace("{not_type}", "notification")
            .replace("{timestamp}", systemTime)
            .replace("{system_time}", systemTime)
            .replace("{package_name}", packageName)
            .replace("{device_uuid}", app.casz.notifybridge.util.DeviceUtil.getDeviceUuid(applicationContext))

        val globalVars = app.casz.notifybridge.data.repository.GlobalVarsRepository.load(applicationContext)
        for (pair in globalVars) {
            resolved = resolved.replace("{global_${pair.first}}", pair.second)
        }
        return resolved
    }

    // --- Simulación de acceso a base de datos y encolamiento ---
    private fun getRulesFromDatabase(): List<RuleEntity> {
        return app.casz.notifybridge.data.repository.RulesRepository.load(applicationContext)
    }

    private fun savePendingDispatch(rule: RuleEntity, payload: String, sourceInfo: String): Long {
        val context = applicationContext
        val dispatches = app.casz.notifybridge.data.repository.DispatchRepository.load(context).toMutableList()
        val newId = System.currentTimeMillis()
        val newDispatch = DispatchEntity(
            id = newId,
            workId = null,
            ruleId = rule.id,
            triggeredBy = sourceInfo,
            targetUrl = rule.httpUrl,
            httpMethod = rule.httpMethod,
            headersJson = rule.headersJson,
            payloadBody = payload,
            status = DispatchStatus.PENDING,
            attempts = 0,
            maxRetries = 3
        )
        dispatches.add(newDispatch)
        app.casz.notifybridge.data.repository.DispatchRepository.save(context, dispatches)
        Log.d("NotificationListener", "Guardando envío pendiente: $payload")
        return newId
    }

    private fun saveFailedDispatch(rule: RuleEntity, payload: String, sourceInfo: String, errorMsg: String) {
        val context = applicationContext
        val dispatches = app.casz.notifybridge.data.repository.DispatchRepository.load(context).toMutableList()
        val newDispatch = DispatchEntity(
            id = System.currentTimeMillis(),
            workId = null,
            ruleId = rule.id,
            triggeredBy = sourceInfo,
            targetUrl = rule.httpUrl,
            httpMethod = rule.httpMethod,
            headersJson = rule.headersJson,
            payloadBody = payload,
            status = DispatchStatus.FAILED,
            attempts = 1,
            maxRetries = 3,
            errorMessage = errorMsg
        )
        dispatches.add(newDispatch)
        app.casz.notifybridge.data.repository.DispatchRepository.save(context, dispatches)
        Log.w("NotificationListener", "Envío fallido por URL inválida: $errorMsg")
    }

    private fun enqueueWork(dispatchId: Long) {
        val workRequest = OneTimeWorkRequest.Builder(DispatchWorker::class.java)
            .setInputData(
                Data.Builder()
                    .putLong("dispatch_id", dispatchId)
                    .build()
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()

        // En una implementación real, actualizaríamos el dispatch en Room con el workRequest.id
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        Log.d("NotificationListener", "WorkManager encolado con ID: ${workRequest.id}")
    }
}
