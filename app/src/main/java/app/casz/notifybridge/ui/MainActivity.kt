package app.casz.notifybridge.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Mock data para simular funcionamiento en tiempo real
    val rulesList = remember {
        mutableStateListOf(
            RuleEntity(
                id = 1,
                name = "regla001",
                source = RuleSource.APP,
                appPackageNames = "com.whatsapp",
                regexPattern = ".*urgente.*",
                httpUrl = "https://casz.app/api/whatsapp",
                httpMethod = "POST",
                headersJson = "{\"Authorization\":\"Bearer token123\"}",
                bodyTemplate = "{\"title\":\"{not_title}\", \"body\":\"{not_text}\"}"
            ),
            RuleEntity(
                id = 2,
                name = "regla002",
                source = RuleSource.SMS,
                appPackageNames = null,
                regexPattern = ".*OTP.*",
                httpUrl = "https://casz.app/api/otp",
                httpMethod = "POST",
                headersJson = "{\"Content-Type\":\"application/json\"}",
                bodyTemplate = "{\"remitente\":\"{not_title}\", \"codigo\":\"{not_text}\"}"
            )
        )
    }

    val createRuleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            val isEdit = data.getBooleanExtra(CreateRuleActivity.EXTRA_IS_EDIT, false)
            val ruleId = data.getLongExtra(CreateRuleActivity.EXTRA_RULE_ID, 0L)

            val rule = RuleEntity(
                id = if (isEdit) ruleId else (rulesList.maxOfOrNull { it.id } ?: 0L) + 1L,
                name = data.getStringExtra(CreateRuleActivity.EXTRA_RULE_NAME) ?: "regla001",
                source = RuleSource.valueOf(data.getStringExtra(CreateRuleActivity.EXTRA_RULE_SOURCE) ?: "APP"),
                appPackageNames = data.getStringExtra(CreateRuleActivity.EXTRA_APP_PACKAGES),
                regexPattern = data.getStringExtra(CreateRuleActivity.EXTRA_REGEX) ?: ".*",
                httpUrl = data.getStringExtra(CreateRuleActivity.EXTRA_HTTP_URL) ?: "",
                httpMethod = data.getStringExtra(CreateRuleActivity.EXTRA_HTTP_METHOD) ?: "POST",
                headersJson = data.getStringExtra(CreateRuleActivity.EXTRA_HEADERS_JSON) ?: "{}",
                bodyTemplate = data.getStringExtra(CreateRuleActivity.EXTRA_BODY_TEMPLATE) ?: ""
            )

            if (isEdit) {
                val idx = rulesList.indexOfFirst { it.id == ruleId }
                if (idx != -1) {
                    rulesList[idx] = rule
                } else {
                    rulesList.add(rule)
                }
            } else {
                rulesList.add(rule)
            }
        }
    }

    val dispatchesList = remember {
        mutableStateListOf(
            DispatchEntity(
                id = 1,
                workId = UUID.randomUUID().toString(),
                ruleId = 1,
                triggeredBy = "App: com.whatsapp",
                targetUrl = "https://casz.app/api/whatsapp",
                httpMethod = "POST",
                headersJson = "{\"Authorization\":\"Bearer token123\"}",
                payloadBody = "{\"title\":\"Juan\", \"body\":\"código urgente 4590\"}",
                status = DispatchStatus.SUCCESS,
                attempts = 1,
                maxRetries = 3,
                responseCode = 200,
                responseBody = "{\"status\":\"ok\"}"
            ),
            DispatchEntity(
                id = 2,
                workId = UUID.randomUUID().toString(),
                ruleId = 2,
                triggeredBy = "SMS from: +15550199",
                targetUrl = "https://casz.app/api/otp",
                httpMethod = "POST",
                headersJson = "{\"Content-Type\":\"application/json\"}",
                payloadBody = "{\"remitente\":\"+15550199\", \"codigo\":\"Su OTP es 123456\"}",
                status = DispatchStatus.PENDING,
                attempts = 0,
                maxRetries = 3
            )
        )
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
                    onClick = {
                        val intent = Intent(context, CreateRuleActivity::class.java).apply {
                            putExtra(CreateRuleActivity.EXTRA_EXISTING_RULES_COUNT, rulesList.size)
                        }
                        createRuleLauncher.launch(intent)
                    },
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
                    onEditRule = { rule ->
                        val intent = Intent(context, CreateRuleActivity::class.java).apply {
                            putExtra(CreateRuleActivity.EXTRA_IS_EDIT, true)
                            putExtra(CreateRuleActivity.EXTRA_RULE_ID, rule.id)
                            putExtra(CreateRuleActivity.EXTRA_RULE_NAME, rule.name)
                            putExtra(CreateRuleActivity.EXTRA_RULE_SOURCE, rule.source.name)
                            putExtra(CreateRuleActivity.EXTRA_APP_PACKAGES, rule.appPackageNames)
                            putExtra(CreateRuleActivity.EXTRA_REGEX, rule.regexPattern)
                            putExtra(CreateRuleActivity.EXTRA_HTTP_URL, rule.httpUrl)
                            putExtra(CreateRuleActivity.EXTRA_HTTP_METHOD, rule.httpMethod)
                            putExtra(CreateRuleActivity.EXTRA_HEADERS_JSON, rule.headersJson)
                            putExtra(CreateRuleActivity.EXTRA_BODY_TEMPLATE, rule.bodyTemplate)
                        }
                        createRuleLauncher.launch(intent)
                    },
                    onDeleteRule = { rule ->
                        rulesList.remove(rule)
                    }
                )
                1 -> DispatchQueueScreen(dispatchesList)
                2 -> GlobalSettingsScreen()
            }
        }
    }
}

// --- PANTALLA DE REGLAS ---
@Composable
fun RulesDashboardScreen(
    rules: List<RuleEntity>,
    onEditRule: (RuleEntity) -> Unit,
    onDeleteRule: (RuleEntity) -> Unit
) {
    var ruleToDelete by remember { mutableStateOf<RuleEntity?>(null) }

    if (rules.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay reglas creadas. Pulsa + para añadir una.", color = TextGray)
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
                    val badgeColor = if (rule.source == RuleSource.SMS) Color(0xFFFF9800) else Color(0xFF4CAF50)
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
fun DispatchQueueScreen(dispatches: MutableList<DispatchEntity>) {
    var queueTab by remember { mutableIntStateOf(0) } // 0: Activos, 1: Historial
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
                            }
                        },
                        onResend = {
                            val index = dispatches.indexOfFirst { it.id == item.id }
                            if (index != -1) {
                                dispatches[index] = item.copy(status = DispatchStatus.PENDING, attempts = 0)
                            }
                        },
                        onDeleteItem = {
                            dispatches.remove(item)
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
fun GlobalSettingsScreen() {
    val context = LocalContext.current
    var retries by remember { mutableStateOf("3") }
    var timeout by remember { mutableStateOf("15") }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    val globalVars = remember { mutableStateListOf("API_KEY" to "12345", "URL_BASE" to "https://casz.app") }

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
            Text("Optimización de Batería", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Spacer(modifier = Modifier.height(4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Ahorro de Batería en Segundo Plano", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextLight)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isIgnoringBattery) "Estado: Excluido (Recomendado)" else "Estado: Optimizado (Puede pausar notificaciones con pantalla apagada)",
                        fontSize = 12.sp,
                        color = if (isIgnoringBattery) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
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
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Configurar Exclusión de Batería", color = Color.White)
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
            Text("Variables Globales", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Text("Usa {global_NOMBRE} en cualquier cuerpo de regla.", fontSize = 12.sp, color = TextGray)
        }

        items(globalVars) { pair ->
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
                        Text(pair.first, fontWeight = FontWeight.Bold, color = TextLight)
                        Text(pair.second, color = TextGray, fontSize = 13.sp)
                    }
                    IconButton(onClick = { globalVars.remove(pair) }) {
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
                    label = { Text("Clave") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    label = { Text("Valor") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (newKey.isNotBlank() && newValue.isNotBlank()) {
                        globalVars.add(newKey to newValue)
                        newKey = ""
                        newValue = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Añadir Variable Global")
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
