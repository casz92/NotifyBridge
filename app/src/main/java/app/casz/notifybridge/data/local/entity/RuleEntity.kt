package app.casz.notifybridge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val source: RuleSource,         // SMS or APP
    val appPackageNames: String?,   // Comma-separated list if APP, or null
    val regexPattern: String,       // RegEx filter
    val regexMatchFields: String = "", // Comma-separated target fields to match (e.g. "title,text")
    val httpUrl: String,            // Destination URL
    val httpMethod: String,         // GET, POST, PUT etc.
    val headersJson: String,        // JSON serialized Map<String, String> of HTTP Headers
    val bodyTemplate: String        // Template for request payload (allows placeholders like {not_text})
)

enum class RuleSource {
    SMS,
    APP,
    IMAP
}
