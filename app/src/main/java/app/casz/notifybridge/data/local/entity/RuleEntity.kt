package app.casz.notifybridge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val source: RuleSource,         // SMS or APP or IMAP
    val appPackageNames: String?,   // Comma-separated list if APP, or null
    val regexPattern: String,       // RegEx filter
    val regexMatchFields: String = "", // Comma-separated target fields to match (e.g. "title,text")
    val httpUrl: String,            // Destination URL
    val httpMethod: String,         // GET, POST, PUT etc.
    val headersJson: String,        // JSON serialized Map<String, String> of HTTP Headers
    val bodyTemplate: String,       // Template for request payload (allows placeholders like {not_text})
    val enabled: Boolean = true,    // Active or inactive status of the rule
    val regexBlocksJson: String = "" // JSON representation of multiple regex blocks
)

enum class RuleSource {
    SMS,
    APP,
    IMAP
}

data class RegexBlock(
    val pattern: String,
    val matchFields: String,
    val nextOperator: String = "NONE" // "AND", "OR", "NONE"
)

fun RuleEntity.getEffectiveRegexBlocks(): List<RegexBlock> {
    if (!regexBlocksJson.isNullOrBlank()) {
        try {
            val arr = org.json.JSONArray(regexBlocksJson)
            val list = mutableListOf<RegexBlock>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    RegexBlock(
                        pattern = obj.optString("pattern", ""),
                        matchFields = obj.optString("matchFields", ""),
                        nextOperator = obj.optString("nextOperator", "NONE")
                    )
                )
            }
            return list
        } catch (e: Exception) {
            // Fallback to legacy single pattern below
        }
    }
    return listOf(
        RegexBlock(
            pattern = regexPattern,
            matchFields = regexMatchFields,
            nextOperator = "NONE"
        )
    )
}

fun RuleEntity.matches(values: Map<String, String>): Boolean {
    val blocks = this.getEffectiveRegexBlocks()
    if (blocks.isEmpty()) return true

    var result = matchBlock(blocks[0], this.source, values)
    var currentOp = blocks[0].nextOperator

    for (i in 1 until blocks.size) {
        val nextMatch = matchBlock(blocks[i], this.source, values)
        result = when (currentOp) {
            "AND" -> result && nextMatch
            "OR" -> result || nextMatch
            else -> result
        }
        currentOp = blocks[i].nextOperator
    }
    return result
}

private fun matchBlock(block: RegexBlock, source: RuleSource, values: Map<String, String>): Boolean {
    val fields = block.matchFields.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    
    return when (source) {
        RuleSource.APP -> {
            val title = values["title"] ?: ""
            val text = values["text"] ?: ""
            val matchTitle = fields.contains("title") || fields.contains("both") || fields.isEmpty()
            val matchText = fields.contains("text") || fields.contains("both") || fields.isEmpty()
            (matchTitle && matchesPattern(title, block.pattern)) ||
            (matchText && matchesPattern(text, block.pattern))
        }
        RuleSource.SMS -> {
            val sender = values["sender"] ?: ""
            val recipient = values["recipient"] ?: ""
            val body = values["body"] ?: ""
            val matchSender = fields.contains("sender")
            val matchRecipient = fields.contains("recipient")
            val matchBody = fields.contains("body") || fields.isEmpty() || fields.contains("all")
            (matchSender && matchesPattern(sender, block.pattern)) ||
            (matchRecipient && recipient.isNotBlank() && matchesPattern(recipient, block.pattern)) ||
            (matchBody && matchesPattern(body, block.pattern))
        }
        RuleSource.IMAP -> {
            val from = values["from"] ?: ""
            val to = values["to"] ?: ""
            val subject = values["subject"] ?: ""
            val body = values["body"] ?: ""
            val matchFrom = fields.contains("from")
            val matchTo = fields.contains("to")
            val matchSubject = fields.contains("subject")
            val matchBody = fields.contains("body") || fields.isEmpty() || fields.contains("all")
            (matchFrom && matchesPattern(from, block.pattern)) ||
            (matchTo && matchesPattern(to, block.pattern)) ||
            (matchSubject && matchesPattern(subject, block.pattern)) ||
            (matchBody && matchesPattern(body, block.pattern))
        }
    }
}

private fun matchesPattern(text: String, patternStr: String): Boolean {
    return try {
        val pattern = java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.CASE_INSENSITIVE)
        pattern.matcher(text).find()
    } catch (e: Exception) {
        false
    }
}
