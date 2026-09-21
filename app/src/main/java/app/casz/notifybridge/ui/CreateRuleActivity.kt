package app.casz.notifybridge.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.data.local.entity.RuleSource
import app.casz.notifybridge.data.local.entity.RegexBlock
import app.casz.notifybridge.data.local.entity.getEffectiveRegexBlocks
import app.casz.notifybridge.data.repository.RulesRepository
import app.casz.notifybridge.ui.theme.AppColors
import app.casz.notifybridge.ui.theme.NotifyBridgeTheme
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class CreateRuleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isEdit = intent.getBooleanExtra(EXTRA_IS_EDIT, false)
        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, 0L)
        val existingRulesCount = intent.getIntExtra(EXTRA_EXISTING_RULES_COUNT, 0)

        val initialName = intent.getStringExtra(EXTRA_RULE_NAME)
        val initialSource = intent.getStringExtra(EXTRA_RULE_SOURCE)
        val initialAppPackages = intent.getStringExtra(EXTRA_APP_PACKAGES)
        val initialRegex = intent.getStringExtra(EXTRA_REGEX)
        val initialRegexFields = intent.getStringExtra(EXTRA_REGEX_FIELDS)
        val initialUrl = intent.getStringExtra(EXTRA_HTTP_URL)
        val initialMethod = intent.getStringExtra(EXTRA_HTTP_METHOD)
        val initialHeadersJson = intent.getStringExtra(EXTRA_HEADERS_JSON)
        val initialBody = intent.getStringExtra(EXTRA_BODY_TEMPLATE)
        val initialEnabled = intent.getBooleanExtra(EXTRA_RULE_ENABLED, true)
        val initialRegexBlocksJson = intent.getStringExtra(EXTRA_REGEX_BLOCKS_JSON)

        setContent {
            NotifyBridgeTheme {
                CreateRuleScreen(
                    isEdit = isEdit,
                    ruleId = ruleId,
                    existingRulesCount = existingRulesCount,
                    initialName = initialName,
                    initialSource = initialSource,
                    initialAppPackages = initialAppPackages,
                    initialRegex = initialRegex,
                    initialRegexFields = initialRegexFields,
                    initialUrl = initialUrl,
                    initialMethod = initialMethod,
                    initialHeadersJson = initialHeadersJson,
                    initialBody = initialBody,
                    initialEnabled = initialEnabled,
                    initialRegexBlocksJson = initialRegexBlocksJson,
                    onBack = {
                        RulesRepository.clearDraft(this@CreateRuleActivity)
                        finish()
                    },
                    onSaveRule = { rule ->
                        RulesRepository.clearDraft(this@CreateRuleActivity)
                        val resultIntent = android.content.Intent().apply {
                            putExtra(EXTRA_IS_EDIT, isEdit)
                            putExtra(EXTRA_RULE_ID, rule.id)
                            putExtra(EXTRA_RULE_NAME, rule.name)
                            putExtra(EXTRA_RULE_SOURCE, rule.source.name)
                            putExtra(EXTRA_APP_PACKAGES, rule.appPackageNames)
                            putExtra(EXTRA_REGEX, rule.regexPattern)
                            putExtra(EXTRA_REGEX_FIELDS, rule.regexMatchFields)
                            putExtra(EXTRA_HTTP_URL, rule.httpUrl)
                            putExtra(EXTRA_HTTP_METHOD, rule.httpMethod)
                            putExtra(EXTRA_HEADERS_JSON, rule.headersJson)
                            putExtra(EXTRA_BODY_TEMPLATE, rule.bodyTemplate)
                            putExtra(EXTRA_RULE_ENABLED, rule.enabled)
                            putExtra(EXTRA_REGEX_BLOCKS_JSON, rule.regexBlocksJson)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_IS_EDIT = "extra_is_edit"
        const val EXTRA_RULE_ID = "extra_rule_id"
        const val EXTRA_EXISTING_RULES_COUNT = "extra_existing_rules_count"
        const val EXTRA_RULE_NAME = "extra_rule_name"
        const val EXTRA_RULE_SOURCE = "extra_rule_source"
        const val EXTRA_APP_PACKAGES = "extra_app_packages"
        const val EXTRA_REGEX = "extra_regex"
        const val EXTRA_REGEX_FIELDS = "extra_regex_fields"
        const val EXTRA_HTTP_URL = "extra_http_url"
        const val EXTRA_HTTP_METHOD = "extra_http_method"
        const val EXTRA_HEADERS_JSON = "extra_headers_json"
        const val EXTRA_BODY_TEMPLATE = "extra_body_template"
        const val EXTRA_RULE_ENABLED = "extra_rule_enabled"
        const val EXTRA_REGEX_BLOCKS_JSON = "extra_regex_blocks_json"
    }
}

data class HeaderInput(val id: Long, var key: String, var value: String)
data class AppItemInfo(val label: String, val packageName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRuleScreen(
    isEdit: Boolean,
    ruleId: Long,
    existingRulesCount: Int,
    initialName: String?,
    initialSource: String?,
    initialAppPackages: String?,
    initialRegex: String?,
    initialRegexFields: String?,
    initialUrl: String?,
    initialMethod: String?,
    initialHeadersJson: String?,
    initialBody: String?,
    initialEnabled: Boolean = true,
    initialRegexBlocksJson: String? = null,
    onBack: () -> Unit,
    onSaveRule: (RuleEntity) -> Unit
) {
    androidx.activity.compose.BackHandler { onBack() }

    val context = LocalContext.current
    val draftObj = remember { RulesRepository.loadDraft(context) }

    var selectedTab by rememberSaveable { mutableIntStateOf(draftObj?.optInt("selectedTab", 0) ?: 0) }

    val autoGeneratedName = remember { String.format("regla%03d", existingRulesCount + 1) }
    var ruleName by rememberSaveable {
        mutableStateOf(
            if (draftObj != null && draftObj.has("ruleName")) draftObj.getString("ruleName")
            else (initialName ?: autoGeneratedName)
        )
    }
    var ruleSource by rememberSaveable {
        mutableStateOf(
            if (draftObj != null && draftObj.has("ruleSource")) RuleSource.valueOf(draftObj.getString("ruleSource"))
            else if (initialSource != null) RuleSource.valueOf(initialSource) else RuleSource.APP
        )
    }
    var selectedPackages by remember {
        mutableStateOf(
            if (draftObj != null && draftObj.has("selectedPackages")) {
                val pkgsStr = draftObj.optString("selectedPackages", "")
                if (pkgsStr.isNotBlank()) pkgsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet() else setOf()
            } else if (!initialAppPackages.isNullOrBlank()) {
                initialAppPackages.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else setOf()
        )
    }

    val initialBlocks = remember {
        val list = mutableListOf<RegexBlock>()
        val draftBlocksJson = if (draftObj != null && draftObj.has("regexBlocksJson")) draftObj.getString("regexBlocksJson") else null
        val blocksJsonToUse = draftBlocksJson ?: (initialRegexBlocksJson ?: "")
        if (blocksJsonToUse.isNotBlank()) {
            try {
                val arr = org.json.JSONArray(blocksJsonToUse)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(RegexBlock(
                        pattern = obj.optString("pattern", ""),
                        matchFields = obj.optString("matchFields", ""),
                        nextOperator = obj.optString("nextOperator", "NONE")
                    ))
                }
            } catch (_: Exception) {}
        }
        if (list.isEmpty()) {
            list.add(RegexBlock(
                pattern = initialRegex ?: ".*",
                matchFields = initialRegexFields ?: "",
                nextOperator = "NONE"
            ))
        }
        list
    }

    val regexBlocks = remember { mutableStateListOf<RegexBlock>().apply { addAll(initialBlocks) } }

    var httpUrl by rememberSaveable {
        mutableStateOf(
            if (draftObj != null && draftObj.has("httpUrl")) draftObj.getString("httpUrl")
            else (initialUrl ?: "https://")
        )
    }
    var httpMethod by rememberSaveable {
        mutableStateOf(
            if (draftObj != null && draftObj.has("httpMethod")) draftObj.getString("httpMethod")
            else (initialMethod ?: "POST")
        )
    }

    val draftHeadersJson: String? = if (draftObj != null && draftObj.has("headersJson")) draftObj.getString("headersJson") else null
    val headersJsonToUse = draftHeadersJson ?: initialHeadersJson

    val initialHeaderInputs = remember {
        val list = mutableListOf<HeaderInput>()
        var idCounter = 1L
        if (!headersJsonToUse.isNullOrBlank()) {
            try {
                val jsonObj = JSONObject(headersJsonToUse)
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    list.add(HeaderInput(idCounter++, k, jsonObj.optString(k, "")))
                }
            } catch (_: Exception) {
                list.add(HeaderInput(idCounter++, "Content-Type", "application/json"))
            }
        } else {
            list.add(HeaderInput(idCounter++, "Content-Type", "application/json"))
        }
        list
    }

    var nextHeaderId by rememberSaveable { mutableLongStateOf((initialHeaderInputs.maxOfOrNull { it.id } ?: 0L) + 1L) }
    val headerInputs = remember { mutableStateListOf<HeaderInput>().apply { addAll(initialHeaderInputs) } }

    val isTextContentType = remember(headersJsonToUse) {
        headerInputs.any { it.key.equals("Content-Type", ignoreCase = true) && it.value.contains("text/plain", ignoreCase = true) }
    }
    var bodyFormat by rememberSaveable {
        mutableStateOf(
            if (draftObj != null && draftObj.has("bodyFormat")) draftObj.getString("bodyFormat")
            else (if (isTextContentType) "TEXT" else "JSON")
        )
    }
    var bodyTemplate by rememberSaveable {
        mutableStateOf(
            if (draftObj != null && draftObj.has("bodyTemplate")) draftObj.getString("bodyTemplate")
            else (initialBody ?: "{\n  \"title\": \"{not_title}\",\n  \"message\": \"{not_text}\"\n}")
        )
    }

    // Auto-save draft on state change
    LaunchedEffect(selectedTab, ruleName, ruleSource, selectedPackages, regexBlocks.toList(), httpUrl, httpMethod, bodyFormat, bodyTemplate, headerInputs.toList()) {
        val headersMap = mutableMapOf<String, String>()
        headerInputs.forEach { h -> if (h.key.isNotBlank()) headersMap[h.key.trim()] = h.value.trim() }
        val blocksArray = org.json.JSONArray()
        regexBlocks.forEach { block ->
            blocksArray.put(org.json.JSONObject().apply {
                put("pattern", block.pattern)
                put("matchFields", block.matchFields)
                put("nextOperator", block.nextOperator)
            })
        }
        val obj = JSONObject().apply {
            put("isEdit", isEdit)
            put("ruleId", ruleId)
            put("existingRulesCount", existingRulesCount)
            put("selectedTab", selectedTab)
            put("ruleName", ruleName)
            put("ruleSource", ruleSource.name)
            put("selectedPackages", selectedPackages.joinToString(","))
            put("regexPattern", regexBlocks.firstOrNull()?.pattern ?: ".*")
            put("regexMatchFields", regexBlocks.firstOrNull()?.matchFields ?: "")
            put("regexBlocksJson", blocksArray.toString())
            put("httpUrl", httpUrl)
            put("httpMethod", httpMethod)
            put("headersJson", JSONObject(headersMap as Map<*, *>).toString())
            put("bodyFormat", bodyFormat)
            put("bodyTemplate", bodyTemplate)
            put("enabled", initialEnabled)
        }
        RulesRepository.saveDraft(context, obj.toString())
    }

    var showAppPicker by remember { mutableStateOf(false) }

    fun updateContentType(format: String) {
        val targetType = if (format == "JSON") "application/json" else "text/plain"
        val existingIndex = headerInputs.indexOfFirst { it.key.equals("Content-Type", ignoreCase = true) }
        if (existingIndex != -1) {
            headerInputs[existingIndex] = headerInputs[existingIndex].copy(value = targetType)
        } else {
            headerInputs.add(HeaderInput(nextHeaderId++, "Content-Type", targetType))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEdit) "Editar Regla" else "Nueva Regla",
                        fontWeight = FontWeight.Bold,
                        color = AppColors.OnSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Atrás",
                            tint = AppColors.OnSurface
                        )
                    }
                },
                actions = {
                    var showSimulation by remember { mutableStateOf(false) }

                    if (showSimulation) {
                        val tempHeadersMap = mutableMapOf<String, String>()
                        headerInputs.forEach { h -> if (h.key.isNotBlank()) tempHeadersMap[h.key.trim()] = h.value.trim() }
                        val blocksArray = org.json.JSONArray()
                        regexBlocks.forEach { block ->
                            blocksArray.put(org.json.JSONObject().apply {
                                put("pattern", block.pattern)
                                put("matchFields", block.matchFields)
                                put("nextOperator", block.nextOperator)
                            })
                        }
                        val tempRule = RuleEntity(
                            id = ruleId,
                            name = ruleName.trim(),
                            source = ruleSource,
                            appPackageNames = if (ruleSource == RuleSource.APP) selectedPackages.joinToString(",") else null,
                            regexPattern = regexBlocks.firstOrNull()?.pattern?.ifBlank { ".*" } ?: ".*",
                            regexMatchFields = regexBlocks.firstOrNull()?.matchFields ?: "",
                            httpUrl = httpUrl.trim(),
                            httpMethod = httpMethod,
                            headersJson = JSONObject(tempHeadersMap as Map<*, *>).toString(),
                            bodyTemplate = bodyTemplate,
                            enabled = true,
                            regexBlocksJson = blocksArray.toString()
                        )
                        app.casz.notifybridge.ui.SimulationDialog(rule = tempRule, onDismiss = { showSimulation = false })
                    }

                    OutlinedButton(
                        onClick = {
                            if (httpUrl.isBlank() || httpUrl == "https://") {
                                Toast.makeText(context, "Ingresa una URL destino para probar", Toast.LENGTH_SHORT).show()
                            } else showSimulation = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(AppColors.Primary)
                        ),
                        modifier = Modifier.padding(end = 6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Probar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            if (ruleName.isBlank()) {
                                Toast.makeText(context, "El nombre es obligatorio", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val trimmedUrl = httpUrl.trim()
                            val parsedUrl = trimmedUrl.toHttpUrlOrNull()
                            if (trimmedUrl.isBlank() || trimmedUrl == "https://" || trimmedUrl == "http://" || parsedUrl == null || parsedUrl.host.isBlank()) {
                                Toast.makeText(context, "Ingresa una URL de destino válida con dominio (ej. https://midominio.com/webhook)", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val headersMap = mutableMapOf<String, String>()
                            headerInputs.forEach { h -> if (h.key.isNotBlank()) headersMap[h.key.trim()] = h.value.trim() }
                            val headersJson = JSONObject(headersMap as Map<*, *>).toString()
                            val blocksArray = org.json.JSONArray()
                            regexBlocks.forEach { block ->
                                blocksArray.put(org.json.JSONObject().apply {
                                    put("pattern", block.pattern)
                                    put("matchFields", block.matchFields)
                                    put("nextOperator", block.nextOperator)
                                })
                            }
                            val rule = RuleEntity(
                                id = ruleId,
                                name = ruleName.trim(),
                                source = ruleSource,
                                appPackageNames = if (ruleSource == RuleSource.APP) selectedPackages.joinToString(",") else null,
                                regexPattern = regexBlocks.firstOrNull()?.pattern?.ifBlank { ".*" } ?: ".*",
                                regexMatchFields = regexBlocks.firstOrNull()?.matchFields ?: "",
                                httpUrl = httpUrl.trim(),
                                httpMethod = httpMethod,
                                headersJson = headersJson,
                                bodyTemplate = bodyTemplate,
                                enabled = draftObj?.optBoolean("enabled", initialEnabled) ?: initialEnabled,
                                regexBlocksJson = blocksArray.toString()
                            )
                            onSaveRule(rule)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface)
            )
        },
        containerColor = AppColors.Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = AppColors.Surface,
                contentColor = AppColors.Primary,
                divider = { Divider(color = AppColors.Divider) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "General",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Headers (${headerInputs.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            "Body ($bodyFormat)",
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (selectedTab) {
                    0 -> GeneralTabContent(
                        ruleName = ruleName,
                        onNameChange = { ruleName = it },
                        ruleSource = ruleSource,
                        onSourceChange = { ruleSource = it },
                        selectedPackages = selectedPackages,
                        onOpenAppPicker = { showAppPicker = true },
                        regexBlocks = regexBlocks,
                        httpUrl = httpUrl,
                        onUrlChange = { httpUrl = it },
                        httpMethod = httpMethod,
                        onMethodChange = { httpMethod = it }
                    )
                    1 -> HeadersTabContent(
                        headerInputs = headerInputs,
                        onAddHeader = { headerInputs.add(HeaderInput(nextHeaderId++, "", "")) },
                        onRemoveHeader = { item -> headerInputs.remove(item) }
                    )
                    2 -> BodyTabContent(
                        bodyFormat = bodyFormat,
                        onFormatChange = { newFormat ->
                            bodyFormat = newFormat
                            updateContentType(newFormat)
                        },
                        bodyTemplate = bodyTemplate,
                        onBodyChange = { bodyTemplate = it }
                    )
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            currentlySelected = selectedPackages,
            onDismiss = { showAppPicker = false },
            onConfirm = { updatedSet ->
                selectedPackages = updatedSet
                showAppPicker = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab: General
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralTabContent(
    ruleName: String,
    onNameChange: (String) -> Unit,
    ruleSource: RuleSource,
    onSourceChange: (RuleSource) -> Unit,
    selectedPackages: Set<String>,
    onOpenAppPicker: () -> Unit,
    regexBlocks: SnapshotStateList<RegexBlock>,
    httpUrl: String,
    onUrlChange: (String) -> Unit,
    httpMethod: String,
    onMethodChange: (String) -> Unit
) {
    val methodsRow1 = listOf("POST", "GET", "PUT")
    val methodsRow2 = listOf("PATCH", "DELETE", "HEAD")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Nombre ──────────────────────────────────────────────────
        item {
            OutlinedTextField(
                value = ruleName,
                onValueChange = onNameChange,
                label = { Text("Nombre de la Regla *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Primary,
                    focusedLabelColor = AppColors.Primary
                ),
                singleLine = true
            )
        }

        // ── Destino HTTP (URL + Método) ──────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            tint = AppColors.Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Destino HTTP", fontWeight = FontWeight.Bold, color = AppColors.OnSurface, fontSize = 15.sp)
                    }

                    OutlinedTextField(
                        value = httpUrl,
                        onValueChange = onUrlChange,
                        label = { Text("URL *") },
                        placeholder = { Text("https://mi-servidor.com/webhook") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.Primary,
                            focusedLabelColor = AppColors.Primary
                        ),
                        singleLine = true
                    )

                    Column {
                        Text("Método HTTP", fontWeight = FontWeight.SemiBold, color = AppColors.OnSurface, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                methodsRow1.forEach { method ->
                                    FilterChip(
                                        selected = httpMethod == method,
                                        onClick = { onMethodChange(method) },
                                        label = {
                                            Text(
                                                method,
                                                fontSize = 12.sp,
                                                fontWeight = if (httpMethod == method) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AppColors.Primary,
                                            selectedLabelColor = AppColors.OnPrimary,
                                            containerColor = AppColors.SurfaceVariant,
                                            labelColor = AppColors.OnSurfaceVar
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                methodsRow2.forEach { method ->
                                    FilterChip(
                                        selected = httpMethod == method,
                                        onClick = { onMethodChange(method) },
                                        label = {
                                            Text(
                                                method,
                                                fontSize = 12.sp,
                                                fontWeight = if (httpMethod == method) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AppColors.Primary,
                                            selectedLabelColor = AppColors.OnPrimary,
                                            containerColor = AppColors.SurfaceVariant,
                                            labelColor = AppColors.OnSurfaceVar
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Origen del Evento ────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Origen del Evento", fontWeight = FontWeight.Bold, color = AppColors.OnSurface, fontSize = 15.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            RuleSource.APP to "📱 App",
                            RuleSource.SMS to "💬 SMS",
                            RuleSource.IMAP to "📧 IMAP"
                        ).forEach { (source, label) ->
                            FilterChip(
                                selected = ruleSource == source,
                                onClick = { onSourceChange(source) },
                                label = { Text(label, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.Primary,
                                    selectedLabelColor = AppColors.OnPrimary,
                                    containerColor = AppColors.SurfaceVariant,
                                    labelColor = AppColors.OnSurfaceVar
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Selector de apps (solo para APP)
                    if (ruleSource == RuleSource.APP) {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenAppPicker() },
                            colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (selectedPackages.isEmpty()) "Todas las apps (por defecto)"
                                        else "${selectedPackages.size} app(s) seleccionada(s)",
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selectedPackages.isEmpty()) AppColors.OnSurfaceVar else AppColors.Primary,
                                        fontSize = 13.sp
                                    )
                                    if (selectedPackages.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            selectedPackages.joinToString(", "),
                                            color = AppColors.TextSubtle,
                                            fontSize = 11.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
                                Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.OnSurfaceVar)
                            }
                        }
                    } else if (ruleSource == RuleSource.IMAP) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = AppColors.PrimaryLight),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ℹ️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Se interceptarán notificaciones de Gmail y se descargará el correo completo automáticamente.",
                                    color = AppColors.PrimaryDark,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Filtros Regex ────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    RegexBlocksEditor(source = ruleSource, blocks = regexBlocks)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab: Headers
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HeadersTabContent(
    headerInputs: List<HeaderInput>,
    onAddHeader: () -> Unit,
    onRemoveHeader: (HeaderInput) -> Unit
) {
    val suggestions = listOf(
        "{not_title}", "{sms_sender}", "{not_text}", "{sms_text}",
        "{package_name}", "{timestamp}", "{global_API_KEY}"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cabeceras HTTP", fontWeight = FontWeight.Bold, color = AppColors.OnSurface, fontSize = 16.sp)
            Button(
                onClick = onAddHeader,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Añadir", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (headerInputs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sin cabeceras. Pulsa 'Añadir' para agregar.", color = AppColors.TextSubtle)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(headerInputs, key = { it.id }) { item ->
                    HeaderInputRow(item = item, suggestions = suggestions, onRemove = { onRemoveHeader(item) })
                }
            }
        }
    }
}

@Composable
fun HeaderInputRow(
    item: HeaderInput,
    suggestions: List<String>,
    onRemove: () -> Unit
) {
    var keyText by remember(item.id) { mutableStateOf(item.key) }
    var valueText by remember(item.id) { mutableStateOf(item.value) }
    var showValueSuggestions by remember { mutableStateOf(false) }

    val filteredSuggestions = remember(valueText) {
        if (valueText.isBlank()) suggestions.take(4)
        else suggestions.filter { it.contains(valueText, ignoreCase = true) }.take(4)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it; item.key = it },
                    label = { Text("Key", fontSize = 12.sp) },
                    placeholder = { Text("Header-Name", fontSize = 12.sp) },
                    modifier = Modifier.weight(0.45f),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        focusedLabelColor = AppColors.Primary
                    )
                )
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it; item.value = it; showValueSuggestions = true },
                    label = { Text("Value", fontSize = 12.sp) },
                    placeholder = { Text("Header-Value", fontSize = 12.sp) },
                    modifier = Modifier.weight(0.55f),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        focusedLabelColor = AppColors.Primary
                    )
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = AppColors.Error, modifier = Modifier.size(18.dp))
                }
            }

            if (showValueSuggestions && filteredSuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    filteredSuggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { valueText = suggestion; item.value = suggestion; showValueSuggestions = false },
                            label = { Text(suggestion, fontSize = 11.sp, color = AppColors.Primary) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = AppColors.PrimaryLight)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab: Body
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyTabContent(
    bodyFormat: String,
    onFormatChange: (String) -> Unit,
    bodyTemplate: String,
    onBodyChange: (String) -> Unit
) {
    var showHelpDialog by remember { mutableStateOf(false) }

    val quickVars = listOf(
        "{not_title}", "{not_text}", "{sms_sender}", "{sms_text}",
        "{package_name}", "{timestamp}", "{device_uuid}",
        "{imap_from}", "{imap_subject}", "{imap_body}"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Format selector + Help button in same row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = bodyFormat == "JSON",
                onClick = { onFormatChange("JSON") },
                label = { Text("JSON", fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Primary,
                    selectedLabelColor = AppColors.OnPrimary,
                    containerColor = AppColors.SurfaceVariant,
                    labelColor = AppColors.OnSurfaceVar
                )
            )
            FilterChip(
                selected = bodyFormat == "TEXT",
                onClick = { onFormatChange("TEXT") },
                label = { Text("Texto", fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Primary,
                    selectedLabelColor = AppColors.OnPrimary,
                    containerColor = AppColors.SurfaceVariant,
                    labelColor = AppColors.OnSurfaceVar
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { showHelpDialog = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AppColors.Primary)
                ),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Variables", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = bodyTemplate,
            onValueChange = onBodyChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = AppColors.OnSurface
            ),
            placeholder = { Text(if (bodyFormat == "JSON") "{\n  \"key\": \"value\"\n}" else "Escribe tu plantilla...") },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Primary,
                unfocusedContainerColor = AppColors.SurfaceVariant,
                focusedContainerColor = AppColors.SurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick-insert variable chips
        Text("Insertar variable:", fontSize = 11.sp, color = AppColors.OnSurfaceVar, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickVars) { varName ->
                SuggestionChip(
                    onClick = { onBodyChange(bodyTemplate + varName) },
                    label = {
                        Text(
                            varName,
                            fontSize = 11.sp,
                            color = AppColors.Primary,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = AppColors.PrimaryLight
                    )
                )
            }
        }
    }

    if (showHelpDialog) {
        VariablesHelpDialog(onDismiss = { showHelpDialog = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Variables Help Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VariablesHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guía de Variables", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.OnSurface)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = AppColors.OnSurfaceVar)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = AppColors.Divider)
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Text("Variables Disponibles", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.OnSurface)
                        Spacer(modifier = Modifier.height(8.dp))
                        VariableHelpItem("{not_title} / {sms_sender}", "Remitente SMS o título de notificación Push.")
                        VariableHelpItem("{not_text} / {sms_text}", "Cuerpo completo del SMS o contenido de la notificación.")
                        VariableHelpItem("{imap_from}", "Dirección del remitente del correo (Modo IMAP).")
                        VariableHelpItem("{imap_to}", "Dirección del destinatario del correo (Modo IMAP).")
                        VariableHelpItem("{imap_subject}", "Asunto del correo recibido (Modo IMAP).")
                        VariableHelpItem("{imap_body}", "Cuerpo completo del correo (Modo IMAP).")
                        VariableHelpItem("{package_name}", "Paquete de la app (ej: com.whatsapp).")
                        VariableHelpItem("{timestamp}", "Marca de tiempo de recepción (Epoch ms).")
                        VariableHelpItem("{device_uuid}", "Identificador único y persistente del dispositivo.")
                        VariableHelpItem("{global_NOMBRE}", "Valor de una Variable Global configurada en Ajustes.")
                    }

                    item {
                        Divider(color = AppColors.Divider)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ejemplos de Plantillas", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Primary)
                    }

                    item {
                        SampleCodeBox(
                            title = "Notificación Push (WhatsApp / App)",
                            code = "{\n  \"app\": \"{package_name}\",\n  \"remitente\": \"{not_title}\",\n  \"mensaje\": \"{not_text}\",\n  \"fecha\": \"{timestamp}\"\n}",
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("template", "{\n  \"app\": \"{package_name}\",\n  \"remitente\": \"{not_title}\",\n  \"mensaje\": \"{not_text}\",\n  \"fecha\": \"{timestamp}\"\n}"))
                                Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    item {
                        SampleCodeBox(
                            title = "SMS Bancario / OTP",
                            code = "{\n  \"origen\": \"{sms_sender}\",\n  \"contenido\": \"{sms_text}\",\n  \"api_token\": \"{global_API_KEY}\"\n}",
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("template", "{\n  \"origen\": \"{sms_sender}\",\n  \"contenido\": \"{sms_text}\",\n  \"api_token\": \"{global_API_KEY}\"\n}"))
                                Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    item {
                        SampleCodeBox(
                            title = "Correo Electrónico (IMAP)",
                            code = "{\n  \"de\": \"{imap_from}\",\n  \"asunto\": \"{imap_subject}\",\n  \"cuerpo\": \"{imap_body}\"\n}",
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("template", "{\n  \"de\": \"{imap_from}\",\n  \"asunto\": \"{imap_subject}\",\n  \"cuerpo\": \"{imap_body}\"\n}"))
                                Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar", color = AppColors.OnPrimary)
                }
            }
        }
    }
}

@Composable
fun VariableHelpItem(name: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = AppColors.PrimaryLight
        ) {
            Text(
                name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = AppColors.Primary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(description, fontSize = 12.sp, color = AppColors.OnSurfaceVar)
    }
}

@Composable
fun SampleCodeBox(title: String, code: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = AppColors.OnSurface)
                TextButton(onClick = onCopy, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(26.dp)) {
                    Text("Copiar", fontSize = 12.sp, color = AppColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = AppColors.PrimaryDark
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  App Picker Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppPickerDialog(
    currentlySelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val tempSelected = remember { mutableStateListOf<String>().apply { addAll(currentlySelected) } }

    var isLoading by remember { mutableStateOf(true) }
    val installedApps = remember { mutableStateListOf<AppItemInfo>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = packages.map { app ->
                AppItemInfo(label = pm.getApplicationLabel(app).toString(), packageName = app.packageName)
            }.sortedBy { it.label.lowercase() }
            withContext(Dispatchers.Main) {
                installedApps.clear()
                installedApps.addAll(list)
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, installedApps.toList()) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Text("Seleccionar Aplicaciones", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.OnSurface)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar app o paquete...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.OnSurfaceVar) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Primary,
                        focusedLabelColor = AppColors.Primary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AppColors.Primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Cargando aplicaciones...", color = AppColors.OnSurfaceVar, fontSize = 13.sp)
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No se encontraron aplicaciones.", color = AppColors.OnSurfaceVar, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isChecked = tempSelected.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) tempSelected.remove(app.packageName)
                                        else tempSelected.add(app.packageName)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) tempSelected.add(app.packageName)
                                        else tempSelected.remove(app.packageName)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = AppColors.Primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.OnSurface)
                                    Text(app.packageName, fontSize = 12.sp, color = AppColors.TextSubtle)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = AppColors.Divider)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = AppColors.OnSurfaceVar) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(tempSelected.toSet()) },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        Text("Aceptar (${tempSelected.size})", color = AppColors.OnPrimary)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Regex Blocks Editor
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegexBlocksEditor(
    source: RuleSource,
    blocks: SnapshotStateList<RegexBlock>
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filtros Regex", fontWeight = FontWeight.Bold, color = AppColors.OnSurface, fontSize = 15.sp)
            Button(
                onClick = {
                    if (blocks.isNotEmpty()) {
                        val lastIdx = blocks.size - 1
                        blocks[lastIdx] = blocks[lastIdx].copy(
                            nextOperator = if (blocks[lastIdx].nextOperator == "NONE") "AND" else blocks[lastIdx].nextOperator
                        )
                    }
                    blocks.add(RegexBlock(pattern = "", matchFields = "", nextOperator = "NONE"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Condición", fontSize = 12.sp)
            }
        }

        blocks.forEachIndexed { index, block ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AppColors.PrimaryLight
                        ) {
                            Text(
                                "Condición #${index + 1}",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.Primary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (blocks.size > 1) {
                            IconButton(
                                onClick = {
                                    blocks.removeAt(index)
                                    if (blocks.isEmpty()) {
                                        blocks.add(RegexBlock(pattern = ".*", matchFields = "", nextOperator = "NONE"))
                                    } else {
                                        val lastIdx = blocks.size - 1
                                        blocks[lastIdx] = blocks[lastIdx].copy(nextOperator = "NONE")
                                    }
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = AppColors.Error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = block.pattern,
                        onValueChange = { newPattern -> blocks[index] = blocks[index].copy(pattern = newPattern) },
                        label = { Text("Regex Pattern", fontSize = 12.sp) },
                        placeholder = { Text("ej: .*OTP.*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.Primary,
                            focusedLabelColor = AppColors.Primary
                        )
                    )

                    RegexMatchFieldsSelector(
                        source = source,
                        selectedFieldsString = block.matchFields,
                        onFieldsChange = { newFields -> blocks[index] = blocks[index].copy(matchFields = newFields) }
                    )
                }
            }

            if (index < blocks.size - 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isAnd = block.nextOperator == "AND"
                    FilterChip(
                        selected = isAnd,
                        onClick = { blocks[index] = blocks[index].copy(nextOperator = "AND") },
                        label = { Text("Y (AND)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Primary,
                            selectedLabelColor = AppColors.OnPrimary,
                            containerColor = AppColors.SurfaceVariant,
                            labelColor = AppColors.OnSurfaceVar
                        )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    FilterChip(
                        selected = !isAnd,
                        onClick = { blocks[index] = blocks[index].copy(nextOperator = "OR") },
                        label = { Text("O (OR)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Primary,
                            selectedLabelColor = AppColors.OnPrimary,
                            containerColor = AppColors.SurfaceVariant,
                            labelColor = AppColors.OnSurfaceVar
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Regex Match Fields Selector
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RegexMatchFieldsSelector(
    source: RuleSource,
    selectedFieldsString: String,
    onFieldsChange: (String) -> Unit
) {
    val selectedFields = remember(selectedFieldsString) {
        selectedFieldsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    val options = when (source) {
        RuleSource.APP  -> listOf("title" to "Título", "text" to "Texto")
        RuleSource.SMS  -> listOf("sender" to "Remitente", "recipient" to "Destinatario", "body" to "Contenido")
        RuleSource.IMAP -> listOf("from" to "De", "to" to "Para", "subject" to "Asunto", "body" to "Cuerpo")
    }

    fun toggleField(field: String) {
        val newFields = if (selectedFields.contains(field)) selectedFields - field else selectedFields + field
        onFieldsChange(newFields.joinToString(","))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Aplicar Regex en:", fontWeight = FontWeight.SemiBold, color = AppColors.OnSurface, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (field, label) ->
                val isSelected = selectedFields.contains(field) || (selectedFields.isEmpty() && (field == "text" || field == "body"))
                FilterChip(
                    selected = isSelected,
                    onClick = { toggleField(field) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.Primary,
                        selectedLabelColor = AppColors.OnPrimary,
                        containerColor = AppColors.SurfaceVariant,
                        labelColor = AppColors.OnSurfaceVar
                    )
                )
            }
        }
    }
}
