package app.casz.notifybridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.data.local.entity.RuleSource
import app.casz.notifybridge.worker.DispatchWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                    val rules = getSmsRulesFromDatabase()
                    for (rule in rules) {
                        if (rule.source == RuleSource.SMS) {
                            val fields = rule.regexMatchFields.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val matchSender = fields.contains("sender")
                            val matchRecipient = fields.contains("recipient")
                            val matchBody = fields.contains("body") || fields.isEmpty() || fields.contains("all")

                            val isMatch = (matchSender && matchesPattern(sender, rule.regexPattern)) ||
                                          (matchRecipient && recipient.isNotBlank() && matchesPattern(recipient, rule.regexPattern)) ||
                                          (matchBody && matchesPattern(body, rule.regexPattern))

                            if (isMatch) {
                                val finalPayload = resolveVariables(
                                    template = rule.bodyTemplate,
                                    sender = sender,
                                    body = body,
                                    systemTime = timestamp
                                )

                                val dispatchId = savePendingDispatch(rule, finalPayload, "SMS from: $sender")

                                enqueueWork(context, dispatchId)
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
        template: String,
        sender: String,
        body: String,
        systemTime: String
    ): String {
        return template
            .replace("{not_title}", sender)
            .replace("{not_text}", body)
            .replace("{system_time}", systemTime)
            .replace("{not_id}", "SMS_MSG")
    }

    // --- Simulación de acceso a base de datos y encolamiento ---
    private suspend fun getSmsRulesFromDatabase(): List<RuleEntity> {
        return listOf(
            RuleEntity(
                id = 2,
                name = "SMS Interceptor Rule",
                source = RuleSource.SMS,
                appPackageNames = null,
                regexPattern = ".*OTP.*",
                httpUrl = "https://midominio.com/api/sms",
                httpMethod = "POST",
                headersJson = "{\"Content-Type\":\"application/json\"}",
                bodyTemplate = "{\"remitente\":\"{not_title}\", \"codigo_otp\":\"{not_text}\", \"recibido\":\"{system_time}\"}"
            )
        )
    }

    private suspend fun savePendingDispatch(rule: RuleEntity, payload: String, sourceInfo: String): Long {
        Log.d("SmsReceiver", "Guardando SMS interceptado en Room: $payload")
        return System.currentTimeMillis()
    }

    private fun enqueueWork(context: Context, dispatchId: Long) {
        val workRequest = OneTimeWorkRequest.Builder(DispatchWorker::class.java)
            .setInputData(
                Data.Builder()
                    .putLong("dispatch_id", dispatchId)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d("SmsReceiver", "WorkManager SMS encolado con ID: ${workRequest.id}")
    }
}
