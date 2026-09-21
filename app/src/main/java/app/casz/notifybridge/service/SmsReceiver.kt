package app.casz.notifybridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.casz.notifybridge.data.local.entity.DispatchEntity
import app.casz.notifybridge.data.local.entity.DispatchStatus
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.data.local.entity.RuleSource
import app.casz.notifybridge.data.local.entity.matches
import app.casz.notifybridge.data.repository.DispatchRepository
import app.casz.notifybridge.data.repository.GlobalVarsRepository
import app.casz.notifybridge.data.repository.RulesRepository
import app.casz.notifybridge.worker.DispatchWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val sender = message.displayOriginatingAddress ?: ""
                val body = message.displayMessageBody ?: ""
                val timestamp = message.timestampMillis.toString()

                var recipient = ""
                try {
                    val subId = intent.getIntExtra("subscription", -1)
                    if (subId != -1) {
                        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                        val info = subscriptionManager?.getActiveSubscriptionInfo(subId)
                        recipient = info?.number ?: ""
                    }
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Error getting subscription number: ${e.message}")
                }

                receiverScope.launch {
                    val rules = getSmsRulesFromDatabase(context)
                    for (rule in rules) {
                        if (!rule.enabled) continue
                        if (rule.source == RuleSource.SMS) {
                            val isMatch = rule.matches(mapOf("sender" to sender, "recipient" to recipient, "body" to body))

                            if (isMatch) {
                                val finalPayload = resolveVariables(
                                    context = context,
                                    template = rule.bodyTemplate,
                                    sender = sender,
                                    body = body,
                                    systemTime = timestamp
                                )

                                val parsedUrl = rule.httpUrl.trim().toHttpUrlOrNull()
                                val isUrlValid = parsedUrl != null && parsedUrl.host.isNotBlank() && rule.httpUrl.trim() != "https://" && rule.httpUrl.trim() != "http://"
                                if (!isUrlValid) {
                                    saveFailedDispatch(context, rule, finalPayload, "SMS from: $sender", "URL de destino no configurada o inválida ('${rule.httpUrl}')")
                                } else {
                                    val dispatchId = savePendingDispatch(context, rule, finalPayload, "SMS from: $sender")
                                    enqueueWork(context, dispatchId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun matchesPattern(text: String, patternStr: String): Boolean {
        return try {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            pattern.matcher(text).find()
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Pattern error: ${e.message}")
            false
        }
    }

    private fun resolveVariables(
        context: Context,
        template: String,
        sender: String,
        body: String,
        systemTime: String
    ): String {
        var resolved = template
            .replace("{not_title}", sender)
            .replace("{sms_sender}", sender)
            .replace("{not_text}", body)
            .replace("{sms_text}", body)
            .replace("{not_id}", "SMS_MSG")
            .replace("{not_type}", "sms")
            .replace("{timestamp}", systemTime)
            .replace("{system_time}", systemTime)
            .replace("{package_name}", "sms")
            .replace("{device_uuid}", app.casz.notifybridge.util.DeviceUtil.getDeviceUuid(context))

        val globalVars = GlobalVarsRepository.load(context)
        for (pair in globalVars) {
            resolved = resolved.replace("{global_${pair.first}}", pair.second)
        }
        return resolved
    }

    private fun getSmsRulesFromDatabase(context: Context): List<RuleEntity> {
        return RulesRepository.load(context)
    }

    private fun savePendingDispatch(context: Context, rule: RuleEntity, payload: String, sourceInfo: String): Long {
        val dispatches = DispatchRepository.load(context).toMutableList()
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
        DispatchRepository.save(context, dispatches)
        Log.d("SmsReceiver", "Guardando SMS interceptado en Room: $payload")
        return newId
    }

    private fun saveFailedDispatch(context: Context, rule: RuleEntity, payload: String, sourceInfo: String, errorMsg: String) {
        val dispatches = DispatchRepository.load(context).toMutableList()
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
        DispatchRepository.save(context, dispatches)
        Log.w("SmsReceiver", "Envío SMS fallido por URL inválida: $errorMsg")
    }

    private fun enqueueWork(context: Context, dispatchId: Long) {
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

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d("SmsReceiver", "WorkManager SMS encolado con ID: ${workRequest.id}")
    }
}
