package app.casz.notifybridge.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.casz.notifybridge.data.local.entity.DispatchEntity
import app.casz.notifybridge.data.local.entity.DispatchStatus
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.data.local.entity.RuleSource
import java.text.SimpleDateFormat
import java.util.*

// Colores personalizados - Azul Vivo
val PrimaryBlue = Color(0xFF007BFF)
val PrimaryLightBlue = Color(0xFFE6F2FF)
val AccentBlue = Color(0xFF0056B3)
val BackgroundDark = Color(0xFF121212)
val CardBackground = Color(0xFF1E1E1E)
val TextLight = Color(0xFFF5F5F5)
val TextGray = Color(0xFFB0B0B0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            NotifyBridgeTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun NotifyBridgeTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = PrimaryBlue,
        onPrimary = Color.White,
        primaryContainer = AccentBlue,
        background = BackgroundDark,
        surface = CardBackground,
        onBackground = TextLight,
        onSurface = TextLight
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}

private const val PREFS_NAME = "notifybridge_prefs"
private const val KEY_GLOBAL_VARS_JSON = "global_variables_map_json"
private const val KEY_RULES_LIST_JSON = "saved_rules_list_json"
private const val KEY_DISPATCHES_LIST_JSON = "saved_dispatches_list_json"

fun loadGlobalVars(context: Context): List<Pair<String, String>> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonString = prefs.getString(KEY_GLOBAL_VARS_JSON, "{}") ?: "{}"
    val list = mutableListOf<Pair<String, String>>()
    try {
        val jsonObj = JSONObject(jsonString)
        val keys = jsonObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObj.optString(key, "")
            if (key.isNotBlank()) {
                list.add(key to value)
            }
        }
    } catch (_: Exception) {}
    return list
}

fun saveGlobalVars(context: Context, vars: List<Pair<String, String>>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonObj = JSONObject()
    vars.forEach { (key, value) ->
        if (key.isNotBlank()) {
            jsonObj.put(key, value)
        }
    }
    prefs.edit().putString(KEY_GLOBAL_VARS_JSON, jsonObj.toString()).apply()
}

fun loadRules(context: Context): List<RuleEntity> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonString = prefs.getString(KEY_RULES_LIST_JSON, "{\"version\":1,\"rules\":[]}") ?: "{\"version\":1,\"rules\":[]}"
    return try {
        val (rules, _) = app.casz.notifybridge.util.RuleJsonUtil.importRulesFromJson(jsonString, 1L)
        rules
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error importando reglas, intentando migrar legacy: ${e.message}")
        try {
            // Intentar migrar formato legacy array
            val legacyArray = org.json.JSONArray(jsonString)
            val migratedObj = org.json.JSONObject().apply {
                put("version", 1)
                put("rules", legacyArray)
            }
            val (rules, _) = app.casz.notifybridge.util.RuleJsonUtil.importRulesFromJson(migratedObj.toString(), 1L)
            saveRules(context, rules)
            rules
        } catch (ex: Exception) {
            android.util.Log.e("MainActivity", "Fallo total al cargar reglas: ${ex.message}")
            emptyList()
        }
    }
}

fun saveRules(context: Context, rules: List<RuleEntity>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonString = app.casz.notifybridge.util.RuleJsonUtil.exportRulesToJson(rules, emptyList())
    prefs.edit().putString(KEY_RULES_LIST_JSON, jsonString).apply()
}

fun loadDispatches(context: Context): List<DispatchEntity> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonString = prefs.getString(KEY_DISPATCHES_LIST_JSON, "[]") ?: "[]"
    val list = mutableListOf<DispatchEntity>()
    try {
        val jsonArr = JSONArray(jsonString)
        for (i in 0 until jsonArr.length()) {
            val obj = jsonArr.getJSONObject(i)
            val entity = DispatchEntity(
                id = obj.optLong("id", 0L),
                workId = if (obj.has("workId") && !obj.isNull("workId")) obj.optString("workId") else null,
                ruleId = obj.optLong("ruleId", 0L),
                triggeredBy = obj.optString("triggeredBy", ""),
                targetUrl = obj.optString("targetUrl", ""),
                httpMethod = obj.optString("httpMethod", "POST"),
                headersJson = obj.optString("headersJson", "{}"),
                payloadBody = obj.optString("payloadBody", ""),
                status = try { DispatchStatus.valueOf(obj.optString("status", "PENDING")) } catch(_: Exception) { DispatchStatus.PENDING },
                attempts = obj.optInt("attempts", 0),
                maxRetries = obj.optInt("maxRetries", 3),
                responseCode = if (obj.has("responseCode") && !obj.isNull("responseCode")) obj.optInt("responseCode") else null,
                responseBody = if (obj.has("responseBody") && !obj.isNull("responseBody")) obj.optString("responseBody") else null,
                errorMessage = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) obj.optString("errorMessage") else null,
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
            list.add(entity)
        }
    } catch (_: Exception) {}
    return list
}

fun saveDispatches(context: Context, dispatches: List<DispatchEntity>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonArr = JSONArray()
    for (item in dispatches) {
        val obj = JSONObject().apply {
            put("id", item.id)
            if (item.workId != null) put("workId", item.workId)
            put("ruleId", item.ruleId)
            put("triggeredBy", item.triggeredBy)
            put("targetUrl", item.targetUrl)
            put("httpMethod", item.httpMethod)
            put("headersJson", item.headersJson)
            put("payloadBody", item.payloadBody)
            put("status", item.status.name)
            put("timestamp", item.timestamp)
            put("attempts", item.attempts)
            put("maxRetries", item.maxRetries)
            if (item.responseCode != null) put("responseCode", item.responseCode)
            if (item.responseBody != null) put("responseBody", item.responseBody)
            if (item.errorMessage != null) put("errorMessage", item.errorMessage)
        }
        jsonArr.put(obj)
    }
    prefs.edit().putString(KEY_DISPATCHES_LIST_JSON, jsonArr.toString()).apply()
}

fun checkSmsPermissions(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // -2L: Ninguno, -1L: Crear nueva regla, >= 0L: Editar regla por ID
    val draftOnStart = remember { loadRuleDraft(context) }
    var activeEditingRuleId by rememberSaveable {
        mutableLongStateOf(
            if (draftOnStart != null) draftOnStart.optLong("ruleId", -1L) else -2L
        )
    }

    var hasSmsPermission by remember { mutableStateOf(checkSmsPermissions(context)) }
    var isNotifListenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var showNotifListenerDialog by remember { mutableStateOf(false) }

    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSmsPermission = permissions[Manifest.permission.RECEIVE_SMS] == true &&
                permissions[Manifest.permission.READ_SMS] == true
        if (hasSmsPermission) {
            Toast.makeText(context, "Permisos de SMS concedidos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Se requieren permisos de SMS para interceptar mensajes", Toast.LENGTH_LONG).show()
        }
        if (!isNotificationListenerEnabled(context)) {
            showNotifListenerDialog = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSmsPermission = checkSmsPermissions(context)
                isNotifListenerEnabled = isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Cargar listas persistidas desde SharedPreferences para mantener estado al minimizar/restaurar
    val rulesList = remember {
        mutableStateListOf<RuleEntity>().apply {
            addAll(loadRules(context))
        }
    }
    val dispatchesList = remember {
        mutableStateListOf<DispatchEntity>().apply {
            addAll(loadDispatches(context))
        }
    }
    val globalVarsList = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            addAll(loadGlobalVars(context))
        }
    }

    LaunchedEffect(Unit) {
        if (!hasSmsPermission) {
            permissionsLauncher.launch(permissionsToRequest)
        } else if (!isNotifListenerEnabled) {
            showNotifListenerDialog = true
        }
    }

    // Export Rules Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonString = app.casz.notifybridge.util.RuleJsonUtil.exportRulesToJson(rulesList, globalVarsList)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "${rulesList.size} reglas exportadas con éxito", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al exportar reglas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Import Rules Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    val nextId = (rulesList.maxOfOrNull { it.id } ?: 0L) + 1L
                    val (importedRules, importedVars) = app.casz.notifybridge.util.RuleJsonUtil.importRulesFromJson(content, startingId = nextId)

                    rulesList.addAll(importedRules)
                    saveRules(context, rulesList)

                    // Merge imported global vars into globalVarsList
                    for (pair in importedVars) {
                        val idx = globalVarsList.indexOfFirst { it.first.equals(pair.first, ignoreCase = true) }
                        if (idx != -1) {
                            globalVarsList[idx] = pair
                        } else {
                            globalVarsList.add(pair)
                        }
                    }
                    saveGlobalVars(context, globalVarsList)

                    Toast.makeText(context, "${importedRules.size} reglas e historias de variables importadas con éxito", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al importar reglas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Si se esta creando o editando una regla, renderizar la pantalla CreateRuleScreen directamente
    if (activeEditingRuleId != -2L) {
        val isEdit = activeEditingRuleId >= 0L
        val targetRule = if (isEdit) rulesList.firstOrNull { it.id == activeEditingRuleId } else null

        CreateRuleScreen(
            isEdit = isEdit,
            ruleId = targetRule?.id ?: 0L,
            existingRulesCount = rulesList.size,
            initialName = targetRule?.name,
            initialSource = targetRule?.source?.name,
            initialAppPackages = targetRule?.appPackageNames,
            initialRegex = targetRule?.regexPattern,
            initialRegexFields = targetRule?.regexMatchFields,
            initialUrl = targetRule?.httpUrl,
            initialMethod = targetRule?.httpMethod,
            initialHeadersJson = targetRule?.headersJson,
            initialBody = targetRule?.bodyTemplate,
            onBack = {
                activeEditingRuleId = -2L
                clearRuleDraft(context)
            },
            onSaveRule = { rule ->
                if (isEdit) {
                    val idx = rulesList.indexOfFirst { it.id == rule.id }
                    if (idx != -1) {
                        rulesList[idx] = rule
                    } else {
                        rulesList.add(rule)
                    }
                } else {
                    val newId = (rulesList.maxOfOrNull { it.id } ?: 0L) + 1L
                    rulesList.add(rule.copy(id = newId))
                }
                saveRules(context, rulesList)
                activeEditingRuleId = -2L
                clearRuleDraft(context)
            }
        )
        return
    }

    // Intercepta el botón de atrás físico en el dashboard principal para evitar que minimice la aplicación
    androidx.activity.compose.BackHandler(enabled = true) {
        // No hace nada para prevenir que se minimice o cierre la app
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "NotifyBridge",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = BackgroundDark) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Reglas") },
                    label = { Text("Reglas") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray,
                        indicatorColor = PrimaryLightBlue.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "Cola") },
                    label = { Text("Envíos") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray,
                        indicatorColor = PrimaryLightBlue.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Configuración") },
                    label = { Text("Ajustes") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray,
                        indicatorColor = PrimaryLightBlue.copy(alpha = 0.1f)
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { activeEditingRuleId = -1L },
                    containerColor = PrimaryBlue,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva Regla")
                }
            }
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> RulesDashboardScreen(
                    rules = rulesList,
                    onEditRule = { rule -> activeEditingRuleId = rule.id },
                    onDeleteRule = { rule ->
                        rulesList.remove(rule)
                        saveRules(context, rulesList)
                    },
                    onExportRules = { exportLauncher.launch("notifybridge_rules.json") },
                    onImportRules = { importLauncher.launch("application/json") }
                )
                1 -> DispatchQueueScreen(
                    dispatches = dispatchesList,
                    onSaveDispatches = { saveDispatches(context, dispatchesList) }
                )
                2 -> GlobalSettingsScreen(
                    globalVars = globalVarsList,
                    hasSmsPermission = hasSmsPermission,
                    isNotifListenerEnabled = isNotifListenerEnabled,
                    onRequestSmsPermission = {
                        permissionsLauncher.launch(permissionsToRequest)
                    }
                )
            }
        }
    }

    if (showNotifListenerDialog && !isNotifListenerEnabled) {
        AlertDialog(
            onDismissRequest = { showNotifListenerDialog = false },
            title = { Text("Permiso de Notificaciones", fontWeight = FontWeight.Bold, color = PrimaryBlue) },
            text = { Text("NotifyBridge requiere el permiso especial de escucha de notificaciones para poder leer y procesar avisos de otras aplicaciones.\n\n¿Deseas abrir los Ajustes del Sistema para activarlo?") },
            confirmButton = {
                Button(
                    onClick = {
                        showNotifListenerDialog = false
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se pudo abrir la configuración de notificaciones", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Abrir Ajustes", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifListenerDialog = false }) {
                    Text("Más tarde", color = TextGray)
                }
            },
            containerColor = CardBackground
        )
    }
}

// --- PANTALLA DE REGLAS ---
@Composable
fun RulesDashboardScreen(
    rules: List<RuleEntity>,
    onEditRule: (RuleEntity) -> Unit,
    onDeleteRule: (RuleEntity) -> Unit,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit
) {
    var ruleToDelete by remember { mutableStateOf<RuleEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reglas (${rules.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportRules,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Importar", fontSize = 11.sp)
                }
                Button(
                    onClick = onExportRules,
                    enabled = rules.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exportar", fontSize = 11.sp)
                }
            }
        }

        if (rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay reglas creadas. Pulsa + o 'Importar' para añadir.", color = TextGray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onEdit = { onEditRule(rule) },
                        onDelete = { ruleToDelete = rule }
                    )
                }
            }
        }
    }

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Eliminar Regla", fontWeight = FontWeight.Bold, color = PrimaryBlue) },
            text = { Text("¿Estás seguro de que deseas eliminar la regla '${rule.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRule(rule)
                        ruleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("Cancelar", color = TextGray)
                }
            },
            containerColor = CardBackground
        )
    }
}

@Composable
fun RuleCard(
    rule: RuleEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rule.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = when (rule.source) {
                        RuleSource.SMS -> Color(0xFFFF9800)
                        RuleSource.APP -> Color(0xFF4CAF50)
                        RuleSource.IMAP -> Color(0xFF9C27B0)
                    }
                    Text(
                        text = rule.source.name,
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(badgeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = TextLight)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.DarkGray)
            Spacer(modifier = Modifier.height(8.dp))

            if (rule.source == RuleSource.APP) {
                Text("Apps: ${rule.appPackageNames}", fontSize = 14.sp, color = TextLight)
            }
            Text("Filtro Regex: \"${rule.regexPattern}\"", fontSize = 14.sp, color = TextLight, fontWeight = FontWeight.SemiBold)
            Text("Destino: [${rule.httpMethod}] ${rule.httpUrl}", fontSize = 14.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --- PANTALLA DE ENVIOS (CON TABS ACTIVOS E HISTORIAL) ---
@Composable
fun DispatchQueueScreen(
    dispatches: MutableList<DispatchEntity>,
    onSaveDispatches: () -> Unit = {}
) {
    var queueTab by rememberSaveable { mutableIntStateOf(0) } // 0: Activos, 1: Historial
    var selectedDispatchDetails by remember { mutableStateOf<DispatchEntity?>(null) }

    val activeDispatches = remember(dispatches.toList(), queueTab) {
        dispatches.filter {
            it.status == DispatchStatus.PENDING ||
            it.status == DispatchStatus.PROCESSING ||
            it.status == DispatchStatus.FAILED
        }
    }

    val historyDispatches = remember(dispatches.toList(), queueTab) {
        dispatches.filter {
            it.status == DispatchStatus.SUCCESS ||
            it.status == DispatchStatus.CANCELLED
        }
    }

    val currentList = if (queueTab == 0) activeDispatches else historyDispatches

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = queueTab,
            containerColor = CardBackground,
            contentColor = PrimaryBlue
        ) {
            Tab(
                selected = queueTab == 0,
                onClick = { queueTab = 0 },
                text = { Text("Activos (${activeDispatches.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = queueTab == 1,
                onClick = { queueTab = 1 },
                text = { Text("Historial (${historyDispatches.size})", fontWeight = FontWeight.Bold) }
            )
        }

        if (queueTab == 1 && historyDispatches.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        dispatches.removeAll {
                            it.status == DispatchStatus.SUCCESS || it.status == DispatchStatus.CANCELLED
                        }
                        onSaveDispatches()
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpiar Historial", color = Color.Red, fontSize = 13.sp)
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (currentList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (queueTab == 0) "No hay envíos activos en la cola." else "No hay historial de envíos.",
                    color = TextGray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(currentList, key = { it.id }) { item ->
                    DispatchItemCard(
                        item = item,
                        isHistoryItem = queueTab == 1,
                        onCancel = {
                            val index = dispatches.indexOfFirst { it.id == item.id }
                            if (index != -1) {
                                dispatches[index] = item.copy(status = DispatchStatus.CANCELLED)
                                onSaveDispatches()
                            }
                        },
                        onResend = {
                            val index = dispatches.indexOfFirst { it.id == item.id }
                            if (index != -1) {
                                dispatches[index] = item.copy(status = DispatchStatus.PENDING, attempts = 0)
                                onSaveDispatches()
                            }
                        },
                        onDeleteItem = {
                            dispatches.remove(item)
                            onSaveDispatches()
                        },
                        onClick = {
                            selectedDispatchDetails = item
                        }
                    )
                }
            }
        }
    }

    selectedDispatchDetails?.let { dispatch ->
        DispatchDetailsDialog(dispatch = dispatch, onDismiss = { selectedDispatchDetails = null })
    }
}

@Composable
fun DispatchItemCard(
    item: DispatchEntity,
    isHistoryItem: Boolean,
    onCancel: () -> Unit,
    onResend: () -> Unit,
    onDeleteItem: () -> Unit,
    onClick: () -> Unit
) {
    val statusColor = when (item.status) {
        DispatchStatus.PENDING -> Color(0xFFFFC107)
        DispatchStatus.PROCESSING -> Color(0xFF03A9F4)
        DispatchStatus.SUCCESS -> Color(0xFF4CAF50)
        DispatchStatus.FAILED -> Color(0xFFE91E63)
        DispatchStatus.CANCELLED -> Color(0xFF9E9E9E)
    }

    val formatter = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault())
    val formattedDate = formatter.format(Date(item.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.triggeredBy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.status.name,
                        fontSize = 10.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(statusColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    if (isHistoryItem) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = onDeleteItem, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color.Red)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Destino: ${item.targetUrl}", fontSize = 13.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Última actualización: $formattedDate • Intentos: ${item.attempts}/${item.maxRetries}", fontSize = 12.sp, color = TextGray)

            if (item.status == DispatchStatus.PENDING || item.status == DispatchStatus.PROCESSING) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Cancelar", fontSize = 11.sp, color = Color.White)
                }
            } else if (item.status == DispatchStatus.FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onResend,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Reenviar", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

// --- PANTALLA DE CONFIGURACION ---
@Composable
fun GlobalSettingsScreen(
    globalVars: SnapshotStateList<Pair<String, String>>,
    hasSmsPermission: Boolean = false,
    isNotifListenerEnabled: Boolean = false,
    onRequestSmsPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    var retries by remember { mutableStateOf("3") }
    var timeout by remember { mutableStateOf("15") }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isIgnoringBattery = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Permisos del Sistema", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Spacer(modifier = Modifier.height(4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // 1. Permisos de SMS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lectura de SMS", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextLight)
                            Text(
                                text = if (hasSmsPermission) "Estado: Concedido" else "Estado: Sin permiso",
                                fontSize = 12.sp,
                                color = if (hasSmsPermission) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            )
                        }
                        if (!hasSmsPermission) {
                            Button(
                                onClick = onRequestSmsPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("Solicitar", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Permiso de Escucha de Notificaciones
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Escucha de Notificaciones", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextLight)
                            Text(
                                text = if (isNotifListenerEnabled) "Estado: Servicio Activo" else "Estado: Inactivo",
                                fontSize = 12.sp,
                                color = if (isNotifListenerEnabled) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            )
                        }
                        if (!isNotifListenerEnabled) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No se pudo abrir la configuración de notificaciones", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("Activar", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    if (!isIgnoringBattery) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Optimización de Batería
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Exclusión de Batería (Doze)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextLight)
                                Text(
                                    text = "Estado: Optimizado (Puede pausar envíos)",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF9800)
                                )
                            }
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(fallbackIntent)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("Excluir", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Ajustes de Solicitudes HTTP", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = retries,
                onValueChange = { retries = it },
                label = { Text("Máximo de Reintentos Global") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = timeout,
                onValueChange = { timeout = it },
                label = { Text("Timeout de Solicitud (segundos)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
            )
        }

        item {
            Divider(color = Color.DarkGray)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Configuración IMAP (Gmail)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Text("Se requiere una App Password de Google (no la contraseña regular).", fontSize = 12.sp, color = TextGray)
            Spacer(modifier = Modifier.height(8.dp))

            val coroutineScope = rememberCoroutineScope()
            val appPrefs = remember { app.casz.notifybridge.data.local.pref.AppPreferences(context) }
            var imapEmailVal by remember { mutableStateOf("") }
            var imapPasswordVal by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                imapEmailVal = appPrefs.imapEmail.first()
                imapPasswordVal = appPrefs.imapAppPassword.first()
            }

            OutlinedTextField(
                value = imapEmailVal,
                onValueChange = { imapEmailVal = it },
                label = { Text("Email") },
                placeholder = { Text("usuario@gmail.com") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = imapPasswordVal,
                onValueChange = { imapPasswordVal = it },
                label = { Text("App Password") },
                placeholder = { Text("xxxx xxxx xxxx xxxx") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        appPrefs.setImapEmail(imapEmailVal.trim())
                        appPrefs.setImapAppPassword(imapPasswordVal.trim())
                        Toast.makeText(context, "Ajustes IMAP guardados", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Guardar Ajustes IMAP")
            }
        }

        item {
            Divider(color = Color.DarkGray)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Variables Globales", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Text("Usa {global_NOMBRE} en cualquier cuerpo de regla.", fontSize = 12.sp, color = TextGray)
        }

        items(globalVars, key = { it.first }) { pair ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("{global_${pair.first}}", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        Text("Valor: ${pair.second}", color = TextGray, fontSize = 13.sp)
                    }
                    IconButton(
                        onClick = {
                            val keyToRemove = pair.first
                            globalVars.removeAll { it.first == keyToRemove }
                            saveGlobalVars(context, globalVars)
                            Toast.makeText(context, "Variable '{global_$keyToRemove}' eliminada", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red)
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
                )
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    label = { Text("Valor") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val trimmedKey = newKey.trim().uppercase().replace(" ", "_")
                    val trimmedValue = newValue.trim()
                    if (trimmedKey.isNotBlank() && trimmedValue.isNotBlank()) {
                        val existingIndex = globalVars.indexOfFirst { it.first.equals(trimmedKey, ignoreCase = true) }
                        if (existingIndex != -1) {
                            globalVars[existingIndex] = trimmedKey to trimmedValue
                            saveGlobalVars(context, globalVars)
                            Toast.makeText(context, "Variable '{global_$trimmedKey}' actualizada", Toast.LENGTH_SHORT).show()
                        } else {
                            globalVars.add(trimmedKey to trimmedValue)
                            saveGlobalVars(context, globalVars)
                            Toast.makeText(context, "Variable '{global_$trimmedKey}' guardada", Toast.LENGTH_SHORT).show()
                        }
                        newKey = ""
                        newValue = ""
                    } else {
                        Toast.makeText(context, "Ingresa Clave y Valor para la variable", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Añadir / Guardar Variable Global")
            }
        }
    }
}



// --- DIALOGO DE DETALLE DE ENVIO ---
@Composable
fun DispatchDetailsDialog(dispatch: DispatchEntity, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Text("Detalle de Envío", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("Origen: ${dispatch.triggeredBy}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("URL: ${dispatch.targetUrl}", fontSize = 13.sp, color = TextGray)
                        Text("Método: ${dispatch.httpMethod}", fontSize = 13.sp, color = TextGray)
                    }
                    item {
                        Divider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Cuerpo Enviado (Payload):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            text = dispatch.payloadBody,
                            fontSize = 12.sp,
                            color = TextLight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        )
                    }
                    item {
                        Divider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Respuesta del Servidor:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Código de Respuesta: ${dispatch.responseCode ?: "N/A"}", fontSize = 13.sp, color = TextGray)
                        Text(
                            text = dispatch.responseBody ?: dispatch.errorMessage ?: "(Sin respuesta aún)",
                            fontSize = 12.sp,
                            color = if (dispatch.errorMessage != null) Color.Red else TextLight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}
