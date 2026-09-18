package app.casz.notifybridge.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.casz.notifybridge.data.local.entity.DispatchEntity
import app.casz.notifybridge.data.local.entity.DispatchStatus
import app.casz.notifybridge.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DispatchCard(
    item: DispatchEntity,
    isHistoryItem: Boolean,
    onCancel: () -> Unit,
    onResend: () -> Unit,
    onDeleteItem: () -> Unit,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val (statusText, statusTextColor, statusBgColor) = when (item.status) {
        DispatchStatus.PENDING    -> Triple("PENDIENTE",   AppColors.StatusPending,    AppColors.StatusPendingBg)
        DispatchStatus.PROCESSING -> Triple("ENVIANDO",    AppColors.StatusProcessing, AppColors.StatusProcessingBg)
        DispatchStatus.SUCCESS    -> Triple("EXITOSO",     AppColors.StatusSuccess,    AppColors.StatusSuccessBg)
        DispatchStatus.FAILED     -> Triple("FALLIDO",     AppColors.StatusFailed,     AppColors.StatusFailedBg)
        DispatchStatus.CANCELLED  -> Triple("CANCELADO",   AppColors.StatusCancelled,  AppColors.StatusCancelledBg)
    }

    val formatter = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
    val formattedDate = formatter.format(Date(item.timestamp))

    // Spin animation for PROCESSING state
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── Top row ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Spinning icon for processing
                    if (item.status == DispatchStatus.PROCESSING) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = AppColors.StatusProcessing,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(rotationAngle)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = item.triggeredBy,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = AppColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(
                        label = statusText,
                        textColor = statusTextColor,
                        bgColor = statusBgColor
                    )

                    if (isHistoryItem) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onDeleteItem,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Borrar",
                                tint = AppColors.Error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box {
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reenviar", color = AppColors.OnSurface) },
                                onClick = { showMenu = false; onResend() }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = AppColors.Error) },
                                onClick = { showMenu = false; onDeleteItem() }
                            )
                        }
                    }
                }
            }

            // ── Info row ────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "→ ${item.targetUrl}",
                fontSize = 12.sp,
                color = AppColors.OnSurfaceVar,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "$formattedDate  •  Intento ${item.attempts}/${item.maxRetries}",
                fontSize = 11.sp,
                color = AppColors.TextSubtle
            )

            // ── Response code chip ──────────────────────────────────────
            if (item.responseCode != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val isSuccess2xx = (item.responseCode ?: 0) in 200..299
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSuccess2xx) AppColors.StatusSuccessBg else AppColors.StatusFailedBg
                ) {
                    Text(
                        "HTTP ${item.responseCode}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSuccess2xx) AppColors.StatusSuccess else AppColors.StatusFailed,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // ── Action buttons ─────────────────────────────────────────
            if (item.status == DispatchStatus.PENDING || item.status == DispatchStatus.PROCESSING) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.OnSurfaceVar
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AppColors.Border)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Cancelar", fontSize = 12.sp)
                }
            } else if (item.status == DispatchStatus.FAILED) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onResend,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Reenviar", fontSize = 12.sp, color = AppColors.OnPrimary)
                }
            }
        }
    }
}
