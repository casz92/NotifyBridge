package app.casz.notifybridge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.ui.components.RuleCard
import app.casz.notifybridge.ui.theme.AppColors

@Composable
fun RulesScreen(
    rules: List<RuleEntity>,
    onEditRule: (RuleEntity) -> Unit,
    onToggleEnabled: (RuleEntity) -> Unit,
    onDeleteRule: (RuleEntity) -> Unit,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit
) {
    var ruleToDelete by remember { mutableStateOf<RuleEntity?>(null) }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportRules,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Importar", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = onExportRules,
                    enabled = rules.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Exportar", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Divider(color = AppColors.Divider)

        // ── List or empty state ──────────────────────────────────────
        if (rules.isEmpty()) {
            EmptyState(
                message = "Aún no hay reglas",
                hint = "Toca el botón + para crear tu primera regla,\no usa Importar para cargar reglas existentes."
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
fun EmptyState(message: String, hint: String) {
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
        }
    }
}
