package app.casz.notifybridge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dispatches")
data class DispatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workId: String?,           // WorkManager UUID
    val ruleId: Long,              // ID of the triggered rule
    val triggeredBy: String,       // e.g. "SMS from +12345" or "App package: com.whatsapp"
    val targetUrl: String,
    val httpMethod: String,
    val headersJson: String,
    val payloadBody: String,
    val status: DispatchStatus,
    val attempts: Int = 0,
    val maxRetries: Int,
    val responseCode: Int? = null,
    val responseBody: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class DispatchStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED
}
