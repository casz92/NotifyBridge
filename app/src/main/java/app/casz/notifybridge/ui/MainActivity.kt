package app.casz.notifybridge.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.content.pm.PackageManager
import app.casz.notifybridge.data.repository.DispatchRepository
import app.casz.notifybridge.data.repository.GlobalVarsRepository
import app.casz.notifybridge.data.repository.RulesRepository
import app.casz.notifybridge.ui.screens.DispatchScreen
import app.casz.notifybridge.ui.screens.RulesScreen
import app.casz.notifybridge.ui.screens.SettingsScreen
import app.casz.notifybridge.ui.theme.AppColors
import app.casz.notifybridge.ui.theme.NotifyBridgeTheme

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

    override fun onStop() {
        super.onStop()
        // Limpia el draft al salir de la app para que el próximo inicio comience limpio
        app.casz.notifybridge.data.repository.RulesRepository.clearDraft(this)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Main Orchestration Screen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Draft restoration: only used during THIS session (not across app restarts)
    var activeEditingRuleId by remember { mutableLongStateOf(-2L) }

    // Permission state
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isNotifListenerEnabled by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }
    var showNotifListenerDialog by remember { mutableStateOf(false) }

    // Permissions launcher
    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasSmsPermission = perms[Manifest.permission.RECEIVE_SMS] == true &&
                perms[Manifest.permission.READ_SMS] == true
        if (!isNotifListenerEnabled) showNotifListenerDialog = true
    }

    // Lifecycle observer — refresh state on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                isNotifListenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Data state — loaded from repositories
    val rulesList = remember {
        mutableStateListOf<app.casz.notifybridge.data.local.entity.RuleEntity>().apply {
            addAll(RulesRepository.load(context))
        }
    }
    val dispatchesList = remember {
        mutableStateListOf<app.casz.notifybridge.data.local.entity.DispatchEntity>().apply {
            addAll(DispatchRepository.load(context))
        }
    }
    val globalVarsList = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            addAll(GlobalVarsRepository.load(context))
        }
    }

    // Initial effects
    LaunchedEffect(Unit) {
        if (!hasSmsPermission) {
            permissionsLauncher.launch(permissionsToRequest)
        } else if (!isNotifListenerEnabled) {
            showNotifListenerDialog = true
        }
    }

    // Auto-refresh dispatches every 2s while on Envíos tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            while (true) {
                val updated = DispatchRepository.load(context)
                if (updated != dispatchesList) {
                    dispatchesList.clear()
                    dispatchesList.addAll(updated)
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    // Export / Import launchers
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = app.casz.notifybridge.util.RuleJsonUtil.exportRulesToJson(rulesList, globalVarsList)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, "${rulesList.size} reglas exportadas", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    val nextId = (rulesList.maxOfOrNull { it.id } ?: 0L) + 1L
                    val (importedRules, importedVars) = app.casz.notifybridge.util.RuleJsonUtil.importRulesFromJson(content, nextId)
                    rulesList.addAll(importedRules)
                    RulesRepository.save(context, rulesList)
                    for (pair in importedVars) {
                        val idx = globalVarsList.indexOfFirst { it.first.equals(pair.first, ignoreCase = true) }
                        if (idx != -1) globalVarsList[idx] = pair else globalVarsList.add(pair)
                    }
                    GlobalVarsRepository.save(context, globalVarsList)
                    Toast.makeText(context, "${importedRules.size} reglas importadas", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Editing rule — renders CreateRuleScreen inline
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
            initialEnabled = targetRule?.enabled ?: true,
            initialRegexBlocksJson = targetRule?.regexBlocksJson,
            onBack = {
                activeEditingRuleId = -2L
                RulesRepository.clearDraft(context)
            },
            onSaveRule = { rule ->
                if (isEdit) {
                    val idx = rulesList.indexOfFirst { it.id == rule.id }
                    if (idx != -1) rulesList[idx] = rule else rulesList.add(rule)
                } else {
                    val newId = (rulesList.maxOfOrNull { it.id } ?: 0L) + 1L
                    rulesList.add(rule.copy(id = newId))
                }
                RulesRepository.save(context, rulesList)
                activeEditingRuleId = -2L
                RulesRepository.clearDraft(context)
            }
        )
        return
    }

    // Prevent back button from closing app on dashboard
    androidx.activity.compose.BackHandler(enabled = true) {}

    // ─── Main Scaffold ───────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = AppColors.Primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "NotifyBridge",
                            fontWeight = FontWeight.Bold,
                            color = AppColors.OnBackground,
                            fontSize = 19.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface,
                    scrolledContainerColor = AppColors.Surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = AppColors.Surface,
                tonalElevation = 2.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            if (selectedTab == 0) Icons.Filled.List else Icons.Outlined.List,
                            contentDescription = "Reglas"
                        )
                    },
                    label = { Text("Reglas", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppColors.Primary,
                        selectedTextColor = AppColors.Primary,
                        unselectedIconColor = AppColors.OnSurfaceVar,
                        unselectedTextColor = AppColors.OnSurfaceVar,
                        indicatorColor = AppColors.PrimaryLight
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            if (selectedTab == 1) Icons.Filled.Send else Icons.Outlined.Send,
                            contentDescription = "Envíos"
                        )
                    },
                    label = { Text("Envíos", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppColors.Primary,
                        selectedTextColor = AppColors.Primary,
                        unselectedIconColor = AppColors.OnSurfaceVar,
                        unselectedTextColor = AppColors.OnSurfaceVar,
                        indicatorColor = AppColors.PrimaryLight
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Ajustes"
                        )
                    },
                    label = { Text("Ajustes", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppColors.Primary,
                        selectedTextColor = AppColors.Primary,
                        unselectedIconColor = AppColors.OnSurfaceVar,
                        unselectedTextColor = AppColors.OnSurfaceVar,
                        indicatorColor = AppColors.PrimaryLight
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { activeEditingRuleId = -1L },
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva Regla")
                }
            }
        },
        containerColor = AppColors.Background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> RulesScreen(
                    rules = rulesList,
                    onEditRule = { rule -> activeEditingRuleId = rule.id },
                    onToggleEnabled = { rule ->
                        val idx = rulesList.indexOfFirst { it.id == rule.id }
                        if (idx != -1) {
                            rulesList[idx] = rule.copy(enabled = !rule.enabled)
                            RulesRepository.save(context, rulesList)
                        }
                    },
                    onDeleteRule = { rule ->
                        rulesList.remove(rule)
                        RulesRepository.save(context, rulesList)
                    },
                    onExportRules = { exportLauncher.launch("notifybridge_rules.json") },
                    onImportRules = { importLauncher.launch("application/json") },
                    onAddPresetRules = { presetsToAdd ->
                        var nextId = (rulesList.maxOfOrNull { it.id } ?: 0L) + 1L
                        for (preset in presetsToAdd) {
                            rulesList.add(preset.copy(id = nextId++))
                        }
                        RulesRepository.save(context, rulesList)
                        val msg = if (presetsToAdd.size == 1) {
                            "Regla \"${presetsToAdd.first().name}\" registrada"
                        } else {
                            "${presetsToAdd.size} reglas predeterminadas registradas"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
                1 -> DispatchScreen(
                    dispatches = dispatchesList,
                    onSaveDispatches = { DispatchRepository.save(context, dispatchesList) }
                )
                2 -> SettingsScreen(
                    globalVars = globalVarsList,
                    hasSmsPermission = hasSmsPermission,
                    isNotifListenerEnabled = isNotifListenerEnabled,
                    onRequestSmsPermission = { permissionsLauncher.launch(permissionsToRequest) }
                )
            }
        }
    }

    // ─── Notification Listener Permission Dialog ─────────────────────────────
    if (showNotifListenerDialog && !isNotifListenerEnabled) {
        AlertDialog(
            onDismissRequest = { showNotifListenerDialog = false },
            title = {
                Text(
                    "Permiso de Notificaciones",
                    fontWeight = FontWeight.Bold,
                    color = AppColors.OnSurface
                )
            },
            text = {
                Text(
                    "NotifyBridge necesita el permiso de escucha de notificaciones para interceptar avisos de otras apps.\n\n¿Abrir los Ajustes del Sistema para activarlo?",
                    color = AppColors.OnSurfaceVar
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotifListenerDialog = false
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        try { context.startActivity(intent) }
                        catch (e: Exception) {
                            Toast.makeText(context, "No se pudo abrir la configuración", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text("Abrir Ajustes", color = AppColors.OnPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifListenerDialog = false }) {
                    Text("Más tarde", color = AppColors.OnSurfaceVar)
                }
            },
            containerColor = AppColors.Surface
        )
    }
}
