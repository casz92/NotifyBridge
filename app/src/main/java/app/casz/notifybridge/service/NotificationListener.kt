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
            // 3. Obtener reglas activas de Room para apps e IMAP
            val rules = getRulesFromDatabase()

            for (rule in rules) {
                if (!rule.enabled) continue
                if (rule.source == RuleSource.APP) {
                    val packages = rule.appPackageNames?.split(",")?.map { it.trim() } ?: emptyList()
                    if (packages.contains(packageName) || packages.contains("*")) {
                        // 4. Verificar filtro Regex usando bloques múltiples (AND/OR)
                        val isMatch = rule.matches(mapOf("title" to title, "text" to processedText))

                        if (isMatch) {
                            // 5. Reemplazar variables en el payload body usando el texto procesado (nuevo)
                            val finalPayload = resolveVariables(
                                template = rule.bodyTemplate,
                                title = title,
                                id = notId,
                                text = processedText,
                                systemTime = systemTime,
                                packageName = packageName
                            )

                            // 6. Crear entidad de Envío (DispatchEntity) con estado PENDING
                            val dispatchId = savePendingDispatch(rule, finalPayload, "App: $packageName")

                            // 7. Encolar la tarea con WorkManager
                            enqueueWork(dispatchId)
                        }
                    }
                } else if (rule.source == RuleSource.IMAP) {
                    // Si interceptamos una notificación de Gmail (com.google.android.gm)
                    if (packageName == "com.google.android.gm") {
                        Log.d("NotificationListener", "Notificación de Gmail recibida en Modo IMAP. Disparando ImapFetchWorker.")
                        enqueueImapFetchWork(rule.id)
                    }
                }
            }

            // Registrar en el historial de procesados e historial de chats si al menos se procesó la notificación
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
        // En una app completa, aquí también se leerían y resolverían las variables globales de DataStore.
        return template
            .replace("{not_title}", title)
            .replace("{not_id}", id)
            .replace("{not_text}", text)
            .replace("{system_time}", systemTime)
            .replace("{package_name}", packageName)
    }

    // --- Simulación de acceso a base de datos y encolamiento ---
    private suspend fun getRulesFromDatabase(): List<RuleEntity> {
        return app.casz.notifybridge.ui.loadRules(applicationContext)
    }

    private suspend fun savePendingDispatch(rule: RuleEntity, payload: String, sourceInfo: String): Long {
        val context = applicationContext
        val dispatches = app.casz.notifybridge.ui.loadDispatches(context).toMutableList()
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
        app.casz.notifybridge.ui.saveDispatches(context, dispatches)
        Log.d("NotificationListener", "Guardando envío pendiente real en Room: $payload")
        return newId
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
