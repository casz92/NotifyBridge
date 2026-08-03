package app.casz.notifybridge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tabla de idempotencia para el modo IMAP.
 * Almacena los Message-ID de los correos ya procesados para evitar
 * reenvíos duplicados incluso si la bandera SEEN se pierde.
 */
@Entity(tableName = "processed_emails")
data class ProcessedEmailEntity(
    @PrimaryKey
    val messageId: String,              // Message-ID extraído del header del correo
    val processedAt: Long = System.currentTimeMillis()  // Timestamp de procesamiento
)
