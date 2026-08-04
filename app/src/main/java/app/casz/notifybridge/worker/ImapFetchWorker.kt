package app.casz.notifybridge.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.casz.notifybridge.data.local.entity.DispatchEntity
import app.casz.notifybridge.data.local.entity.DispatchStatus
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.data.local.entity.RuleSource
import app.casz.notifybridge.data.local.pref.AppPreferences
import app.casz.notifybridge.ui.loadRules
import app.casz.notifybridge.ui.loadDispatches
import app.casz.notifybridge.ui.saveDispatches
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.Properties
import java.util.regex.Pattern
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store
import javax.mail.search.FlagTerm
import javax.mail.Flags
import javax.mail.Message
import javax.mail.internet.MimeMessage

class ImapFetchWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val ruleId = inputData.getLong("rule_id", -1L)
        if (ruleId == -1L) {
            return Result.failure()
        }

        // Cargar reglas e identificar la regla actual
        val rules = loadRules(appContext)
        val rule = rules.firstOrNull { it.id == ruleId && it.source == RuleSource.IMAP && it.enabled }
            ?: return Result.failure()

        // Cargar credenciales de DataStore
        val appPrefs = AppPreferences(appContext)
        val imapEmail = appPrefs.imapEmail.first()
        val imapAppPassword = appPrefs.imapAppPassword.first()

        if (imapEmail.isBlank() || imapAppPassword.isBlank()) {
            Log.e("ImapFetchWorker", "Credenciales IMAP no configuradas")
            return Result.failure()
        }

        var store: Store? = null
        var inbox: Folder? = null
        try {
            val properties = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", "imap.gmail.com")
                put("mail.imaps.port", "993")
                put("mail.imaps.ssl.enable", "true")
            }

            val session = Session.getDefaultInstance(properties, null)
            store = session.getStore("imaps")
            store.connect("imap.gmail.com", imapEmail, imapAppPassword)

            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)

            // Buscar mensajes UNSEEN (no leídos)
            val unseenMessages = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
            if (unseenMessages.isEmpty()) {
                Log.d("ImapFetchWorker", "No se encontraron correos no leídos (UNSEEN)")
                return Result.success()
            }

            // Ordenar por fecha descendente o simplemente tomar el último
            val message = unseenMessages.last() // El más reciente de los no leídos

            // 1. Extraer Message-ID para control de duplicación
            val mimeMessage = message as? MimeMessage
            val messageId = mimeMessage?.messageID ?: "${message.subject}_${message.sentDate?.time}"

            // Comprobar idempotencia contra SharedPreferences (simulando tabla Room ProcessedEmails)
            if (isEmailAlreadyProcessed(messageId)) {
                Log.d("ImapFetchWorker", "Mensaje ya procesado (Message-ID duplicado): $messageId")
                return Result.success()
            }

            // 2. Extraer asunto, cuerpo y direcciones del mensaje
            val subject = message.subject ?: ""
            val body = getTextFromMessage(message)
            val fromAddresses = message.from?.joinToString(",") { it.toString() } ?: ""
            val toAddresses = message.getRecipients(Message.RecipientType.TO)?.joinToString(",") { it.toString() } ?: ""

            // 3. Evaluar Regex de la regla según los campos especificados
            val fields = rule.regexMatchFields.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val matchFrom = fields.contains("from")
            val matchTo = fields.contains("to")
            val matchSubject = fields.contains("subject")
            val matchBody = fields.contains("body") || fields.isEmpty() || fields.contains("all")

            val isMatch = (matchFrom && matchesPattern(fromAddresses, rule.regexPattern)) ||
                          (matchTo && matchesPattern(toAddresses, rule.regexPattern)) ||
                          (matchSubject && matchesPattern(subject, rule.regexPattern)) ||
                          (matchBody && matchesPattern(body, rule.regexPattern))

            if (isMatch) {
                // Resolver variables
                val systemTime = System.currentTimeMillis().toString()
                val finalPayload = resolveVariables(
                    template = rule.bodyTemplate,
                    subject = subject,
                    body = body,
                    from = fromAddresses,
                    to = toAddresses,
                    systemTime = systemTime
                )

                // Guardar despacho simulando base de datos (usando prefs compartidas de la UI)
                val dispatchId = savePendingDispatch(rule, finalPayload, "IMAP: $subject")

                // Encolar DispatchWorker
                enqueueDispatchWorker(dispatchId)

                // Marcar como procesado en la tabla de idempotencia local
                markEmailAsProcessed(messageId)
            } else {
                Log.d("ImapFetchWorker", "El correo no coincide con la expresión regular: ${rule.regexPattern}")
            }

            // Marcar el mensaje como leído en el servidor IMAP
            message.setFlag(Flags.Flag.SEEN, true)

        } catch (e: Exception) {
            Log.e("ImapFetchWorker", "Error durante el procesamiento IMAP: ${e.message}", e)
            return Result.retry()
        } finally {
            try {
                inbox?.close(true)
                store?.close()
            } catch (e: Exception) {
                Log.e("ImapFetchWorker", "Error cerrando recursos IMAP: ${e.message}")
            }
        }

        return Result.success()
    }

    private fun matchesPattern(text: String, patternStr: String): Boolean {
        return try {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            pattern.matcher(text).find()
        } catch (e: Exception) {
            Log.e("ImapFetchWorker", "Pattern error: ${e.message}")
            false
        }
    }

    private fun resolveVariables(
        template: String,
        subject: String,
        body: String,
        from: String,
        to: String,
        systemTime: String
    ): String {
        return template
            .replace("{imap_subject}", subject)
            .replace("{imap_body}", body)
            .replace("{imap_from}", from)
            .replace("{imap_to}", to)
            .replace("{timestamp}", systemTime)
            .replace("{system_time}", systemTime)
    }

    private fun getTextFromMessage(message: Message): String {
        return when {
            message.isMimeType("text/plain") -> message.content.toString()
            message.isMimeType("multipart/*") -> {
                val mimeMultipart = message.content as? javax.mail.Multipart
                mimeMultipart?.let { getTextFromMimeMultipart(it) } ?: ""
            }
            else -> message.content?.toString() ?: ""
        }
    }

    private fun getTextFromMimeMultipart(mimeMultipart: javax.mail.Multipart): String {
        val result = StringBuilder()
        val count = mimeMultipart.count
        for (i in 0 until count) {
            val bodyPart = mimeMultipart.getBodyPart(i)
            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.content.toString())
            } else if (bodyPart.isMimeType("text/html")) {
                // Opcional: ignorar html o parsearlo
            } else if (bodyPart.content is javax.mail.Multipart) {
                result.append(getTextFromMimeMultipart(bodyPart.content as javax.mail.Multipart))
            }
        }
        return result.toString()
    }

    private fun isEmailAlreadyProcessed(messageId: String): Boolean {
        val prefs = appContext.getSharedPreferences("imap_processed_emails", Context.MODE_PRIVATE)
        return prefs.contains(messageId)
    }

    private fun markEmailAsProcessed(messageId: String) {
        val prefs = appContext.getSharedPreferences("imap_processed_emails", Context.MODE_PRIVATE)
        prefs.edit().putLong(messageId, System.currentTimeMillis()).apply()
    }

    private fun savePendingDispatch(rule: RuleEntity, payload: String, sourceInfo: String): Long {
        val dispatches = loadDispatches(appContext).toMutableList()
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
        saveDispatches(appContext, dispatches)
        return newId
    }

    private fun enqueueDispatchWorker(dispatchId: Long) {
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
        WorkManager.getInstance(appContext).enqueue(workRequest)
    }
}
