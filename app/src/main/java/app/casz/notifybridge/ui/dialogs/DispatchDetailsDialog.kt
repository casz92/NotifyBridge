package app.casz.notifybridge.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.casz.notifybridge.data.local.entity.DispatchEntity
import app.casz.notifybridge.ui.components.StatusChip
import app.casz.notifybridge.ui.theme.AppColors
import app.casz.notifybridge.data.local.entity.DispatchStatus

@Composable
fun DispatchDetailsDialog(dispatch: DispatchEntity, onDismiss: () -> Unit) {
    val (statusText, statusTextColor, statusBgColor) = when (dispatch.status) {
        DispatchStatus.PENDING    -> Triple("PENDIENTE",  AppColors.StatusPending,    AppColors.StatusPendingBg)
        DispatchStatus.PROCESSING -> Triple("ENVIANDO",   AppColors.StatusProcessing, AppColors.StatusProcessingBg)
        DispatchStatus.SUCCESS    -> Triple("EXITOSO",    AppColors.StatusSuccess,    AppColors.StatusSuccessBg)
        DispatchStatus.FAILED     -> Triple("FALLIDO",    AppColors.StatusFailed,     AppColors.StatusFailedBg)
        DispatchStatus.CANCELLED  -> Triple("CANCELADO",  AppColors.StatusCancelled,  AppColors.StatusCancelledBg)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                // ── Header ──────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Detalle de Envío",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.OnSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StatusChip(
                            label = statusText,
                            textColor = statusTextColor,
                            bgColor = statusBgColor
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = AppColors.OnSurfaceVar
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = AppColors.Divider)
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        DetailSection(title = "Origen") {
                            DetailRow("Disparado por", dispatch.triggeredBy)
                            DetailRow("Método", dispatch.httpMethod)
                            DetailRow("URL", dispatch.targetUrl)
                        }
                    }
                    item {
                        DetailSection(title = "Cuerpo Enviado (Payload)") {
                            CodeBlock(text = dispatch.payloadBody)
                        }
                    }
                    item {
                        DetailSection(title = "Respuesta del Servidor") {
                            if (dispatch.responseCode != null) {
                                val isSuccess = (dispatch.responseCode ?: 0) in 200..299
                                DetailRow(
                                    "Código HTTP",
                                    dispatch.responseCode.toString(),
                                    valueColor = if (isSuccess) AppColors.StatusSuccess else AppColors.StatusFailed
                                )
                            }
                            val bodyOrError = dispatch.responseBody ?: dispatch.errorMessage ?: "(Sin respuesta aún)"
                            val isError = dispatch.errorMessage != null
                            CodeBlock(
                                text = bodyOrError,
                                textColor = if (isError) AppColors.Error else AppColors.OnSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text("Cerrar", color = AppColors.OnPrimary)
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Primary
        )
        content()
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = AppColors.OnSurface
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            fontSize = 13.sp,
            color = AppColors.OnSurfaceVar,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 80.dp)
        )
        Text(
            value,
            fontSize = 13.sp,
            color = valueColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CodeBlock(text: String, textColor: androidx.compose.ui.graphics.Color = AppColors.OnSurface) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.SurfaceVariant
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            modifier = Modifier.padding(10.dp)
        )
    }
}
