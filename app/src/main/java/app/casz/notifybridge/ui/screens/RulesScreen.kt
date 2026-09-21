package app.casz.notifybridge.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.data.local.entity.RuleSource
import app.casz.notifybridge.data.repository.RulesRepository
import app.casz.notifybridge.ui.components.RuleCard
import app.casz.notifybridge.ui.theme.AppColors

@Composable
fun RulesScreen(
    rules: List<RuleEntity>,
    onEditRule: (RuleEntity) -> Unit,
    onToggleEnabled: (RuleEntity) -> Unit,
    onDeleteRule: (RuleEntity) -> Unit,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onAddPresetRules: (List<RuleEntity>) -> Unit
) {
    var ruleToDelete by remember { mutableStateOf<RuleEntity?>(null) }
    var showPresetsDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Toolbar strip ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${rules.size} ${if (rules.size == 1) "regla" else "reglas"}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.OnSurfaceVar
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { showPresetsDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Plantillas", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = onImportRules,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Importar", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = onExportRules,
                    enabled = rules.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exportar", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Divider(color = AppColors.Divider)

        // ── List or empty state ──────────────────────────────────────
        if (rules.isEmpty()) {
            EmptyState(
                message = "Aún no hay reglas",
                hint = "Toca el botón + para crear una regla desde cero,\no activa las reglas predeterminadas para Nequi y Bancolombia.",
                onOpenPresets = { showPresetsDialog = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onEdit = { onEditRule(rule) },
                        onToggleEnabled = { onToggleEnabled(rule) },
                        onDelete = { ruleToDelete = rule }
                    )
                }
            }
        }
    }

    // ── Presets Dialog ───────────────────────────────────────────────
    if (showPresetsDialog) {
        PresetRulesDialog(
            currentRules = rules,
            onDismiss = { showPresetsDialog = false },
            onAddPresetRule = { preset ->
                onAddPresetRules(listOf(preset))
            },
            onAddAllPending = { pendingList ->
                onAddPresetRules(pendingList)
            }
        )
    }

    // ── Delete confirmation dialog ───────────────────────────────────
    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = {
                Text(
                    "Eliminar regla",
                    fontWeight = FontWeight.Bold,
                    color = AppColors.OnSurface
                )
            },
            text = {
                Text(
                    "¿Eliminar \"${rule.name}\"? Esta acción no se puede deshacer.",
                    color = AppColors.OnSurfaceVar
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDeleteRule(rule); ruleToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                ) {
                    Text("Eliminar", color = AppColors.OnPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("Cancelar", color = AppColors.Primary)
                }
            },
            containerColor = AppColors.Surface
        )
    }
}

@Composable
fun PresetRulesDialog(
    currentRules: List<RuleEntity>,
    onDismiss: () -> Unit,
    onAddPresetRule: (RuleEntity) -> Unit,
    onAddAllPending: (List<RuleEntity>) -> Unit
) {
    val presets = remember { RulesRepository.getDefaultRules() }
    val pendingPresets = presets.filter { preset -> currentRules.none { it.name == preset.name } }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = AppColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Reglas Predeterminadas",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.OnSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Plantillas listas para detectar pagos y transferencias de Nequi y Bancolombia.",
                    fontSize = 13.sp,
                    color = AppColors.OnSurfaceVar,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(presets, key = { it.name }) { preset ->
                        val isAdded = currentRules.any { it.name == preset.name }
                        val isApp = preset.source == RuleSource.APP

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AppColors.SurfaceElevated,
                            border = BorderStroke(1.dp, AppColors.Divider),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = preset.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.OnSurface
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isApp) AppColors.SourceAppBg else AppColors.SourceSmsBg
                                        ) {
                                            Text(
                                                text = if (isApp) "APP" else "SMS",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isApp) AppColors.SourceApp else AppColors.SourceSms,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = if (isApp) {
                                                if (preset.name.contains("Nequi", ignoreCase = true)) "com.nequi.MobileApp" else "App Bancolombia"
                                            } else {
                                                "Mensaje de texto SMS"
                                            },
                                            fontSize = 11.sp,
                                            color = AppColors.TextSubtle
                                        )
                                    }
                                }

                                if (isAdded) {
                                    OutlinedButton(
                                        onClick = {},
                                        enabled = false,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            disabledContainerColor = AppColors.PrimaryLight.copy(alpha = 0.5f),
                                            disabledContentColor = AppColors.Primary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Activa", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Button(
                                        onClick = { onAddPresetRule(preset) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Añadir", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pendingPresets.isNotEmpty()) {
                        Button(
                            onClick = { onAddAllPending(pendingPresets) },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryDark),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Añadir todas (${pendingPresets.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cerrar", color = AppColors.Primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    hint: String,
    onOpenPresets: (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = message,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.OnSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                fontSize = 14.sp,
                color = AppColors.OnSurfaceVar,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (onOpenPresets != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onOpenPresets,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ver reglas predeterminadas", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
