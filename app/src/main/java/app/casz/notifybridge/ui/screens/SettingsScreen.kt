package app.casz.notifybridge.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.casz.notifybridge.data.repository.GlobalVarsRepository
import app.casz.notifybridge.ui.theme.AppColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    globalVars: SnapshotStateList<Pair<String, String>>,
    hasSmsPermission: Boolean,
    isNotifListenerEnabled: Boolean,
    onRequestSmsPermission: () -> Unit
) {
    val context = LocalContext.current
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isIgnoringBattery = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    val allPermissionsOk = hasSmsPermission && isNotifListenerEnabled && isIgnoringBattery

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Section 1: Variables Globales ──────────────────────────────
        item {
            SectionHeader(title = "Variables Globales", icon = "⚙️")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Usa {global_NOMBRE} en cualquier body o URL de regla para inyectar valores dinámicos.",
                fontSize = 12.sp,
                color = AppColors.OnSurfaceVar
            )
        }

        if (globalVars.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No hay variables globales aún.\nPulsa «Añadir Variable» para crear una.",
                            color = AppColors.OnSurfaceVar,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        items(globalVars, key = { it.first }) { pair ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "{global_${pair.first}}",
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Primary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            pair.second,
                            color = AppColors.OnSurfaceVar,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(
                        onClick = {
                            val keyToRemove = pair.first
                            globalVars.removeAll { it.first == keyToRemove }
                            GlobalVarsRepository.save(context, globalVars)
                            Toast.makeText(context, "Variable eliminada", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = AppColors.Error)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Nueva variable",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.OnSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newKey,
                            onValueChange = { newKey = it },
                            label = { Text("Nombre") },
                            placeholder = { Text("API_KEY") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Primary,
                                focusedLabelColor = AppColors.Primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newValue,
                            onValueChange = { newValue = it },
                            label = { Text("Valor") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Primary,
                                focusedLabelColor = AppColors.Primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                    Button(
                        onClick = {
                            val trimmedKey = newKey.trim().uppercase().replace(" ", "_")
                            val trimmedValue = newValue.trim()
                            if (trimmedKey.isNotBlank() && trimmedValue.isNotBlank()) {
                                val existingIndex = globalVars.indexOfFirst { it.first.equals(trimmedKey, ignoreCase = true) }
                                if (existingIndex != -1) {
                                    globalVars[existingIndex] = trimmedKey to trimmedValue
                                } else {
                                    globalVars.add(trimmedKey to trimmedValue)
                                }
                                GlobalVarsRepository.save(context, globalVars)
                                Toast.makeText(context, "Variable guardada", Toast.LENGTH_SHORT).show()
                                newKey = ""
                                newValue = ""
                            } else {
                                Toast.makeText(context, "Ingresa nombre y valor", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        Text("Añadir Variable", color = AppColors.OnPrimary)
                    }
                }
            }
        }

        // ── Section 2: Ajustes HTTP ────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(title = "Comportamiento HTTP", icon = "🌐")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    var retries by remember { mutableStateOf(3f) }
                    var timeout by remember { mutableStateOf(15f) }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Reintentos máximos", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.OnSurface)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppColors.PrimaryLight
                            ) {
                                Text(
                                    "${retries.toInt()}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.Primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Slider(
                            value = retries,
                            onValueChange = { retries = it },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = AppColors.Primary,
                                activeTrackColor = AppColors.Primary,
                                inactiveTrackColor = AppColors.PrimaryLight
                            )
                        )
                        Text("0 = Sin reintentos", fontSize = 11.sp, color = AppColors.OnSurfaceVar)
                    }

                    Divider(color = AppColors.Divider)

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Timeout de solicitud", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.OnSurface)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppColors.PrimaryLight
                            ) {
                                Text(
                                    "${timeout.toInt()}s",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.Primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Slider(
                            value = timeout,
                            onValueChange = { timeout = it },
                            valueRange = 5f..60f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = AppColors.Primary,
                                activeTrackColor = AppColors.Primary,
                                inactiveTrackColor = AppColors.PrimaryLight
                            )
                        )
                        Text("Rango: 5s – 60s", fontSize = 11.sp, color = AppColors.OnSurfaceVar)
                    }
                }
            }
        }

        // ── Section 3: IMAP (Gmail) ────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(title = "Integración IMAP (Gmail)", icon = "📧")
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.PrimaryLight),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Requiere una App Password de Google, NO tu contraseña normal. Créala en myaccount.google.com → Seguridad → Contraseñas de aplicación.",
                        fontSize = 12.sp,
                        color = AppColors.PrimaryDark
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val coroutineScope = rememberCoroutineScope()
                    val appPrefs = remember { app.casz.notifybridge.data.local.pref.AppPreferences(context) }
                    var imapEmail by remember { mutableStateOf("") }
                    var imapPassword by remember { mutableStateOf("") }

                    LaunchedEffect(Unit) {
                        imapEmail = appPrefs.imapEmail.first()
                        imapPassword = appPrefs.imapAppPassword.first()
                    }

                    OutlinedTextField(
                        value = imapEmail,
                        onValueChange = { imapEmail = it },
                        label = { Text("Email") },
                        placeholder = { Text("usuario@gmail.com") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Email, contentDescription = null, tint = AppColors.OnSurfaceVar)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.Primary,
                            focusedLabelColor = AppColors.Primary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = imapPassword,
                        onValueChange = { imapPassword = it },
                        label = { Text("App Password") },
                        placeholder = { Text("xxxx xxxx xxxx xxxx") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = AppColors.OnSurfaceVar)
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.Primary,
                            focusedLabelColor = AppColors.Primary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                appPrefs.setImapEmail(imapEmail.trim())
                                appPrefs.setImapAppPassword(imapPassword.trim())
                                Toast.makeText(context, "Ajustes IMAP guardados", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        Text("Guardar Ajustes IMAP", color = AppColors.OnPrimary)
                    }
                }
            }
        }

        // ── Section 4: Permisos del Sistema ───────────────────────────
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeader(title = "Permisos del Sistema", icon = "🔐")
                if (allPermissionsOk) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = AppColors.StatusSuccessBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✓", fontSize = 12.sp, color = AppColors.StatusSuccess, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Todo OK", fontSize = 12.sp, color = AppColors.StatusSuccess, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    PermissionRow(
                        icon = Icons.Default.Send,
                        title = "Lectura de SMS",
                        description = "Necesario para interceptar mensajes SMS",
                        isGranted = hasSmsPermission,
                        actionLabel = "Solicitar",
                        onAction = onRequestSmsPermission
                    )

                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = AppColors.Divider
                    )

                    PermissionRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Escucha de Notificaciones",
                        description = "Necesario para interceptar notificaciones",
                        isGranted = isNotifListenerEnabled,
                        actionLabel = "Activar",
                        onAction = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            try { context.startActivity(intent) }
                            catch (e: Exception) {
                                Toast.makeText(context, "No se pudo abrir ajustes", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    if (!isIgnoringBattery) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = AppColors.Divider
                        )
                        PermissionRow(
                            icon = Icons.Outlined.Warning,
                            title = "Exclusión de Batería (Doze)",
                            description = "Evita que el sistema suspenda los envíos",
                            isGranted = false,
                            isWarning = true,
                            actionLabel = "Excluir",
                            onAction = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    try { context.startActivity(intent) }
                                    catch (e: Exception) {
                                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(fallback)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, icon: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.OnBackground
        )
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isWarning: Boolean = false,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    isGranted -> AppColors.StatusSuccess
                    isWarning -> AppColors.Warning
                    else      -> AppColors.OnSurfaceVar
                },
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.OnSurface)
                Text(
                    text = if (isGranted) "✓ Activo" else description,
                    fontSize = 12.sp,
                    color = if (isGranted) AppColors.StatusSuccess else AppColors.OnSurfaceVar
                )
            }
        }
        if (!isGranted) {
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWarning) AppColors.Warning else AppColors.Primary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.OnPrimary)
            }
        }
    }
}
