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

        serviceScope.launch {
            // 1. Obtener reglas activas de Room para apps e IMAP
            val rules = getRulesFromDatabase()

            for (rule in rules) {
                if (!rule.enabled) continue
                if (rule.source == RuleSource.APP) {
                    val packages = rule.appPackageNames?.split(",")?.map { it.trim() } ?: emptyList()
                    if (packages.contains(packageName) || packages.contains("*")) {
                        // 2. Verificar filtro Regex contra los campos especificados
                        val fields = rule.regexMatchFields.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val matchTitle = fields.contains("title") || fields.contains("both") || fields.isEmpty()
                        val matchText = fields.contains("text") || fields.contains("both") || fields.isEmpty()

                        val isMatch = (matchTitle && matchesPattern(title, rule.regexPattern)) ||
                                      (matchText && matchesPattern(text, rule.regexPattern))

                        if (isMatch) {
                            // 3. Reemplazar variables en el payload body
                            val finalPayload = resolveVariables(
                                template = rule.bodyTemplate,
                                title = title,
                                id = notId,
                                text = text,
                                systemTime = systemTime,
                                packageName = packageName
                            )

                            // 4. Crear entidad de Envío (DispatchEntity) con estado PENDING
                            val dispatchId = savePendingDispatch(rule, finalPayload, "App: $packageName")

                            // 5. Encolar la tarea con WorkManager
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
        }
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
