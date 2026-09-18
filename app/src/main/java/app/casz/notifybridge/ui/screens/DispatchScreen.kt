package app.casz.notifybridge.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.casz.notifybridge.data.local.entity.DispatchEntity
import app.casz.notifybridge.data.local.entity.DispatchStatus
import app.casz.notifybridge.ui.components.DispatchCard
import app.casz.notifybridge.ui.dialogs.DispatchDetailsDialog
import app.casz.notifybridge.ui.theme.AppColors

@Composable
fun DispatchScreen(
    dispatches: MutableList<DispatchEntity>,
    onSaveDispatches: () -> Unit
) {
    val context = LocalContext.current
    var queueTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedDispatchDetails by remember { mutableStateOf<DispatchEntity?>(null) }

    val activeDispatches = remember(dispatches.toList()) {
        dispatches.filter {
            it.status == DispatchStatus.PENDING ||
            it.status == DispatchStatus.PROCESSING ||
            it.status == DispatchStatus.FAILED
        }
    }
    val historyDispatches = remember(dispatches.toList()) {
        dispatches.filter {
            it.status == DispatchStatus.SUCCESS ||
            it.status == DispatchStatus.CANCELLED
        }
    }
    val currentList = if (queueTab == 0) activeDispatches else historyDispatches

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Tabs ────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = queueTab,
            containerColor = AppColors.Surface,
            contentColor = AppColors.Primary,
            divider = { Divider(color = AppColors.Divider) }
        ) {
            Tab(
                selected = queueTab == 0,
                onClick = { queueTab = 0 },
                text = {
                    Text(
                        "Activos (${activeDispatches.size})",
                        fontWeight = if (queueTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = queueTab == 1,
                onClick = { queueTab = 1 },
                text = {
                    Text(
                        "Historial (${historyDispatches.size})",
                        fontWeight = if (queueTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }

        // ── History actions bar ──────────────────────────────────────
        if (queueTab == 1 && historyDispatches.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        dispatches.removeAll {
                            it.status == DispatchStatus.SUCCESS ||
                            it.status == DispatchStatus.CANCELLED
                        }
                        onSaveDispatches()
                    }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = AppColors.Error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpiar Historial", color = AppColors.Error, fontSize = 13.sp)
                }
            }
            Divider(color = AppColors.Divider)
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ── Content ─────────────────────────────────────────────────
        if (currentList.isEmpty()) {
            EmptyState(
                message = if (queueTab == 0) "Sin envíos activos" else "Historial vacío",
                hint = if (queueTab == 0)
                    "Los envíos en cola o fallidos aparecerán aquí."
                else
                    "Los envíos completados y cancelados aparecerán aquí."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentList, key = { it.id }) { item ->
                    DispatchCard(
                        item = item,
                        isHistoryItem = queueTab == 1,
                        onCancel = {
                            val index = dispatches.indexOfFirst { it.id == item.id }
                            if (index != -1) {
                                try {
                                    item.workId?.let { workIdStr ->
                                        val uuid = java.util.UUID.fromString(workIdStr)
                                        androidx.work.WorkManager.getInstance(context)
                                            .cancelWorkById(uuid)
                                    }
                                } catch (e: Exception) {
                                    Log.e("DispatchScreen", "Error cancelando work: ${e.message}")
                                }
                                dispatches[index] = item.copy(status = DispatchStatus.CANCELLED)
                                onSaveDispatches()
                            }
                        },
                        onResend = {
                            val index = dispatches.indexOfFirst { it.id == item.id }
                            if (index != -1) {
                                val workRequest = androidx.work.OneTimeWorkRequest
                                    .Builder(app.casz.notifybridge.worker.DispatchWorker::class.java)
                                    .setInputData(
                                        androidx.work.Data.Builder()
                                            .putLong("dispatch_id", item.id)
                                            .build()
                                    )
                                    .setBackoffCriteria(
                                        androidx.work.BackoffPolicy.EXPONENTIAL,
                                        androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                                        java.util.concurrent.TimeUnit.MILLISECONDS
                                    )
                                    .build()
                                androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                                dispatches[index] = item.copy(
                                    status = DispatchStatus.PENDING,
                                    attempts = 0,
                                    workId = workRequest.id.toString()
                                )
                                onSaveDispatches()
                            }
                        },
                        onDeleteItem = {
                            dispatches.remove(item)
                            onSaveDispatches()
                        },
                        onClick = { selectedDispatchDetails = item }
                    )
                }
            }
        }
    }

    selectedDispatchDetails?.let { dispatch ->
        DispatchDetailsDialog(dispatch = dispatch, onDismiss = { selectedDispatchDetails = null })
    }
}
