package app.casz.notifybridge.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.data.local.entity.RuleSource
import app.casz.notifybridge.data.local.entity.getEffectiveRegexBlocks
import app.casz.notifybridge.ui.theme.AppColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RuleCard(
    rule: RuleEntity,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onEdit,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) AppColors.Surface else AppColors.SurfaceElevated
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (rule.enabled) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header row: name + badges ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rule.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rule.enabled) AppColors.OnSurface else AppColors.OnSurfaceVar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!rule.enabled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            StatusChip(
                                label = "INACTIVA",
                                textColor = AppColors.StatusFailed,
                                bgColor = AppColors.StatusFailedBg
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(rule.source)
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Opciones",
                                tint = AppColors.OnSurfaceVar,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (rule.enabled) "Desactivar" else "Activar",
                                        color = AppColors.OnSurface
                                    )
                                },
                                onClick = { showMenu = false; onToggleEnabled() }
                            )
                            DropdownMenuItem(
                                text = { Text("Editar", color = AppColors.OnSurface) },
                                onClick = { showMenu = false; onEdit() }
                            )
                            Divider(color = AppColors.Divider)
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = AppColors.Error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = AppColors.Error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = { showMenu = false; onDelete() }
                            )
                        }
                    }
                }
            }

            // ── Divider ────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = AppColors.Divider)
            Spacer(modifier = Modifier.height(10.dp))

            // ── Details ────────────────────────────────────────────────
            if (rule.source == RuleSource.APP && !rule.appPackageNames.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Apps: ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.OnSurfaceVar
                    )
                    Text(
                        rule.appPackageNames ?: "",
                        fontSize = 13.sp,
                        color = AppColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            val blocks = rule.getEffectiveRegexBlocks()
            val regexSummary = if (blocks.size > 1) {
                "\"${blocks.first().pattern}\" (+${blocks.size - 1} más)"
            } else {
                "\"${rule.regexPattern}\""
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Regex: ",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.OnSurfaceVar
                )
                Text(
                    regexSummary,
                    fontSize = 13.sp,
                    color = AppColors.Primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AppColors.PrimaryLight,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        rule.httpMethod,
                        fontSize = 11.sp,
                        color = AppColors.Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    rule.httpUrl,
                    fontSize = 13.sp,
                    color = AppColors.OnSurfaceVar,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SourceBadge(source: RuleSource) {
    val (text, textColor, bgColor) = when (source) {
        RuleSource.SMS  -> Triple("SMS",  AppColors.SourceSms,  AppColors.SourceSmsBg)
        RuleSource.APP  -> Triple("APP",  AppColors.SourceApp,  AppColors.SourceAppBg)
        RuleSource.IMAP -> Triple("IMAP", AppColors.SourceImap, AppColors.SourceImapBg)
    }
    StatusChip(label = text, textColor = textColor, bgColor = bgColor)
}

@Composable
fun StatusChip(label: String, textColor: Color, bgColor: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
