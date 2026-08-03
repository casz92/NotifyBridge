package app.casz.notifybridge.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import androidx.compose.foundation.lazy.LazyColumn
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
import org.json.JSONObject

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
        val initialUrl = intent.getStringExtra(EXTRA_HTTP_URL)
        val initialMethod = intent.getStringExtra(EXTRA_HTTP_METHOD)
        val initialHeadersJson = intent.getStringExtra(EXTRA_HEADERS_JSON)
        val initialBody = intent.getStringExtra(EXTRA_BODY_TEMPLATE)

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
                    initialUrl = initialUrl,
                    initialMethod = initialMethod,
                    initialHeadersJson = initialHeadersJson,
                    initialBody = initialBody,
                    onBack = {
                        clearRuleDraft(this@CreateRuleActivity)
                        finish()
                    },
                    onSaveRule = { rule ->
                        clearRuleDraft(this@CreateRuleActivity)
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_IS_EDIT, isEdit)
                            putExtra(EXTRA_RULE_ID, rule.id)
                            putExtra(EXTRA_RULE_NAME, rule.name)
                            putExtra(EXTRA_RULE_SOURCE, rule.source.name)
                            putExtra(EXTRA_APP_PACKAGES, rule.appPackageNames)
                            putExtra(EXTRA_REGEX, rule.regexPattern)
                            putExtra(EXTRA_HTTP_URL, rule.httpUrl)
                            putExtra(EXTRA_HTTP_METHOD, rule.httpMethod)
                            putExtra(EXTRA_HEADERS_JSON, rule.headersJson)
                            putExtra(EXTRA_BODY_TEMPLATE, rule.bodyTemplate)
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
        const val EXTRA_HTTP_URL = "extra_http_url"
        const val EXTRA_HTTP_METHOD = "extra_http_method"
        const val EXTRA_HEADERS_JSON = "extra_headers_json"
        const val EXTRA_BODY_TEMPLATE = "extra_body_template"
    }
}

private const val DRAFT_PREFS_NAME = "notifybridge_draft_prefs"
private const val KEY_ACTIVE_DRAFT_JSON = "active_rule_draft_json"

fun clearRuleDraft(context: Context) {
    val prefs = context.getSharedPreferences(DRAFT_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_ACTIVE_DRAFT_JSON).apply()
}

fun saveRuleDraft(context: Context, jsonString: String) {
    val prefs = context.getSharedPreferences(DRAFT_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_ACTIVE_DRAFT_JSON, jsonString).apply()
}

fun loadRuleDraft(context: Context): JSONObject? {
    val prefs = context.getSharedPreferences(DRAFT_PREFS_NAME, Context.MODE_PRIVATE)
    val str = prefs.getString(KEY_ACTIVE_DRAFT_JSON, null) ?: return null
    return try { JSONObject(str) } catch (_: Exception) { null }
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
    initialUrl: String?,
    initialMethod: String?,
    initialHeadersJson: String?,
    initialBody: String?,
    onBack: () -> Unit,
    onSaveRule: (RuleEntity) -> Unit
) {
    val context = LocalContext.current
    val draftObj = remember { loadRuleDraft(context) }

    var selectedTab by rememberSaveable { mutableIntStateOf(draftObj?.optInt("selectedTab", 0) ?: 0) }

    // State Fields
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
            } else {
                setOf()
            }
        )
    }
    var regexPattern by rememberSaveable {
        mutableStateOf(
            if (draftObj != null && draftObj.has("regexPattern")) draftObj.getString("regexPattern")
            else (initialRegex ?: ".*")
        )
    }
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

    // Headers list initialization
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
                    val v = jsonObj.optString(k, "")
                    list.add(HeaderInput(idCounter++, k, v))
                }
            } catch (e: Exception) {
                list.add(HeaderInput(idCounter++, "Content-Type", "application/json"))
            }
        } else {
            list.add(HeaderInput(idCounter++, "Content-Type", "application/json"))
        }
        list
    }

    var nextHeaderId by rememberSaveable { mutableLongStateOf((initialHeaderInputs.maxOfOrNull { it.id } ?: 0L) + 1L) }
    val headerInputs = remember { mutableStateListOf<HeaderInput>().apply { addAll(initialHeaderInputs) } }

    // Body initialization
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

    // Guardado automatico en tiempo real del borrador
    LaunchedEffect(selectedTab, ruleName, ruleSource, selectedPackages, regexPattern, httpUrl, httpMethod, bodyFormat, bodyTemplate, headerInputs.toList()) {
        val headersMap = mutableMapOf<String, String>()
        headerInputs.forEach { h ->
            if (h.key.isNotBlank()) {
                headersMap[h.key.trim()] = h.value.trim()
            }
        }
        val obj = JSONObject().apply {
            put("isEdit", isEdit)
            put("ruleId", ruleId)
            put("existingRulesCount", existingRulesCount)
            put("selectedTab", selectedTab)
            put("ruleName", ruleName)
            put("ruleSource", ruleSource.name)
            put("selectedPackages", selectedPackages.joinToString(","))
            put("regexPattern", regexPattern)
            put("httpUrl", httpUrl)
            put("httpMethod", httpMethod)
            put("headersJson", JSONObject(headersMap as Map<*, *>).toString())
            put("bodyFormat", bodyFormat)
            put("bodyTemplate", bodyTemplate)
        }
        saveRuleDraft(context, obj.toString())
    }

    // Dialog state for app selector
    var showAppPicker by remember { mutableStateOf(false) }

    // Auto-update Content-Type header when body format changes
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
                title = { Text(if (isEdit) "Editar Regla" else "Nueva Regla", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (ruleName.isBlank()) {
                                Toast.makeText(context, "El nombre de la regla es obligatorio", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (httpUrl.isBlank()) {
                                Toast.makeText(context, "La URL destino es obligatoria", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // Build Headers JSON
                            val headersMap = mutableMapOf<String, String>()
                            headerInputs.forEach { h ->
                                if (h.key.isNotBlank()) {
                                    headersMap[h.key.trim()] = h.value.trim()
                                }
                            }
                            val headersJson = JSONObject(headersMap as Map<*, *>).toString()

                            val rule = RuleEntity(
                                id = ruleId,
                                name = ruleName.trim(),
                                source = ruleSource,
                                appPackageNames = if (ruleSource == RuleSource.APP) selectedPackages.joinToString(",") else null,
                                regexPattern = regexPattern.ifBlank { ".*" },
                                httpUrl = httpUrl.trim(),
                                httpMethod = httpMethod,
                                headersJson = headersJson,
                                bodyTemplate = bodyTemplate
                            )
                            onSaveRule(rule)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Header
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CardBackground,
                contentColor = PrimaryBlue
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("General", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Headers (${headerInputs.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Body ($bodyFormat)", fontWeight = FontWeight.Bold) }
                )
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> GeneralTabContent(
                        ruleName = ruleName,
                        onNameChange = { ruleName = it },
                        ruleSource = ruleSource,
                        onSourceChange = { ruleSource = it },
                        selectedPackages = selectedPackages,
                        onOpenAppPicker = { showAppPicker = true },
                        regexPattern = regexPattern,
                        onRegexChange = { regexPattern = it },
                        httpUrl = httpUrl,
                        onUrlChange = { httpUrl = it },
                        httpMethod = httpMethod,
                        onMethodChange = { httpMethod = it }
                    )
                    1 -> HeadersTabContent(
                        headerInputs = headerInputs,
                        onAddHeader = {
                            headerInputs.add(HeaderInput(nextHeaderId++, "", ""))
                        },
                        onRemoveHeader = { item ->
                            headerInputs.remove(item)
                        }
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

// --- TAB GENERAL ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralTabContent(
    ruleName: String,
    onNameChange: (String) -> Unit,
    ruleSource: RuleSource,
    onSourceChange: (RuleSource) -> Unit,
    selectedPackages: Set<String>,
    onOpenAppPicker: () -> Unit,
    regexPattern: String,
    onRegexChange: (String) -> Unit,
    httpUrl: String,
    onUrlChange: (String) -> Unit,
    httpMethod: String,
    onMethodChange: (String) -> Unit
) {
    var expandedMethodDropdown by remember { mutableStateOf(false) }
    val availableMethods = listOf("GET", "POST", "PUT", "DELETE", "HEAD", "PATCH", "OPTIONS")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = ruleName,
                onValueChange = onNameChange,
                label = { Text("Nombre de la Regla *") },
                supportingText = { Text("Obligatorio (autogenerado por defecto)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
            )
        }

        item {
            Text("Origen del Evento", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = ruleSource == RuleSource.APP,
                    onClick = { onSourceChange(RuleSource.APP) },
                    label = { Text("Aplicaciones") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryBlue,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = ruleSource == RuleSource.SMS,
                    onClick = { onSourceChange(RuleSource.SMS) },
                    label = { Text("SMS Interceptor") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryBlue,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (ruleSource == RuleSource.APP) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAppPicker() },
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Aplicaciones Seleccionadas", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (selectedPackages.isEmpty()) {
                            Text("Click para seleccionar apps instaladas (Todas por defecto)", color = TextGray, fontSize = 13.sp)
                        } else {
                            Text(
                                text = "${selectedPackages.size} app(s): ${selectedPackages.joinToString(", ")}",
                                color = PrimaryBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = regexPattern,
                onValueChange = onRegexChange,
                label = { Text("Filtro Expresión Regular (RegEx)") },
                supportingText = { Text("Ejemplo: .*urgente.* o .*OTP.*") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
            )
        }

        item {
            OutlinedTextField(
                value = httpUrl,
                onValueChange = onUrlChange,
                label = { Text("URL Destino HTTP *") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
            )
        }

        item {
            Text("Método HTTP", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))

            ExposedDropdownMenuBox(
                expanded = expandedMethodDropdown,
                onExpandedChange = { expandedMethodDropdown = !expandedMethodDropdown }
            ) {
                OutlinedTextField(
                    value = httpMethod,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Método") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMethodDropdown) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
                )
                ExposedDropdownMenu(
                    expanded = expandedMethodDropdown,
                    onDismissRequest = { expandedMethodDropdown = false }
                ) {
                    availableMethods.forEach { method ->
                        DropdownMenuItem(
                            text = { Text(method, fontWeight = FontWeight.Bold) },
                            onClick = {
                                onMethodChange(method)
                                expandedMethodDropdown = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- TAB HEADERS ---
@Composable
fun HeadersTabContent(
    headerInputs: List<HeaderInput>,
    onAddHeader: () -> Unit,
    onRemoveHeader: (HeaderInput) -> Unit
) {
    val suggestions = listOf(
        "{not_title}",
        "{sms_sender}",
        "{not_text}",
        "{sms_text}",
        "{package_name}",
        "{timestamp}",
        "{global_API_KEY}"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cabeceras HTTP", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 16.sp)
            Button(
                onClick = onAddHeader,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Añadir Header", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (headerInputs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay cabeceras añadidas. Pulsa 'Añadir Header'.", color = TextGray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(headerInputs, key = { it.id }) { item ->
                    HeaderInputRow(
                        item = item,
                        suggestions = suggestions,
                        onRemove = { onRemoveHeader(item) }
                    )
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
        if (valueText.isBlank()) {
            suggestions.take(4)
        } else {
            suggestions.filter { it.contains(valueText, ignoreCase = true) }.take(4)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = keyText,
                    onValueChange = {
                        keyText = it
                        item.key = it
                    },
                    label = { Text("Key", fontSize = 12.sp) },
                    placeholder = { Text("Header-Name", fontSize = 12.sp) },
                    modifier = Modifier.weight(0.45f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
                )

                OutlinedTextField(
                    value = valueText,
                    onValueChange = {
                        valueText = it
                        item.value = it
                        showValueSuggestions = true
                    },
                    label = { Text("Value", fontSize = 12.sp) },
                    placeholder = { Text("Header-Value", fontSize = 12.sp) },
                    modifier = Modifier.weight(0.55f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
                )

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red, modifier = Modifier.size(18.dp))
                }
            }

            // Autocomplete Suggestion Chips (Max 4)
            if (showValueSuggestions && filteredSuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filteredSuggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = {
                                valueText = suggestion
                                item.value = suggestion
                                showValueSuggestions = false
                            },
                            label = { Text(suggestion, fontSize = 11.sp, color = PrimaryBlue) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = PrimaryLightBlue.copy(alpha = 0.15f))
                        )
                    }
                }
            }
        }
    }
}

// --- TAB BODY ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyTabContent(
    bodyFormat: String,
    onFormatChange: (String) -> Unit,
    bodyTemplate: String,
    onBodyChange: (String) -> Unit
) {
    var showHelpDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Formato del Payload", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = bodyFormat == "JSON",
                onClick = { onFormatChange("JSON") },
                label = { Text("JSON (application/json)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryBlue,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = bodyFormat == "TEXT",
                onClick = { onFormatChange("TEXT") },
                label = { Text("Texto Plano (text/plain)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryBlue,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Editor del Cuerpo (Body Template):", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextLight)
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = bodyTemplate,
            onValueChange = onBodyChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            placeholder = { Text(if (bodyFormat == "JSON") "{\n  \"key\": \"value\"\n}" else "Escribe tu plantilla...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                focusedContainerColor = Color.Black.copy(alpha = 0.5f)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showHelpDialog = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Guía de Variables y Ejemplos", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showHelpDialog) {
        VariablesHelpDialog(onDismiss = { showHelpDialog = false })
    }
}

// --- MODAL DE AYUDA DE VARIABLES Y PLANTILLAS ---
@Composable
fun VariablesHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guía de Variables", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text("Variables Disponibles", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextLight)
                        Spacer(modifier = Modifier.height(6.dp))
                        VariableHelpItem("{not_title} / {sms_sender}", "Número de teléfono del remitente en SMS (ej: +56912345678) o título de la notificación Push en Apps.")
                        VariableHelpItem("{not_text} / {sms_text}", "Texto/cuerpo completo del mensaje SMS o contenido de la notificación.")
                        VariableHelpItem("{package_name}", "Nombre del paquete de la app que envió la notificación (ej: com.whatsapp) o com.android.mms en SMS.")
                        VariableHelpItem("{timestamp}", "Marca de tiempo de recepción (Epoch ms).")
                        VariableHelpItem("{global_NOMBRE}", "Inserta el valor de cualquier Variable Global configurada en los Ajustes.")
                    }

                    item {
                        Divider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ejemplos de Plantillas", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryBlue)
                    }

                    item {
                        SampleCodeBox(
                            title = "Ejemplo 1: Notificación Push (WhatsApp / App)",
                            code = "{\n  \"app\": \"{package_name}\",\n  \"remitente\": \"{not_title}\",\n  \"mensaje\": \"{not_text}\",\n  \"fecha\": \"{timestamp}\"\n}",
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("JSON Sample", "{\n  \"app\": \"{package_name}\",\n  \"remitente\": \"{not_title}\",\n  \"mensaje\": \"{not_text}\",\n  \"fecha\": \"{timestamp}\"\n}"))
                                Toast.makeText(context, "Ejemplo 1 copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    item {
                        SampleCodeBox(
                            title = "Ejemplo 2: Mensajes SMS Bancario / OTP",
                            code = "{\n  \"origen\": \"{sms_sender}\",\n  \"contenido\": \"{sms_text}\",\n  \"api_token\": \"{global_API_KEY}\"\n}",
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("SMS Sample", "{\n  \"origen\": \"{sms_sender}\",\n  \"contenido\": \"{sms_text}\",\n  \"api_token\": \"{global_API_KEY}\"\n}"))
                                Toast.makeText(context, "Ejemplo 2 copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun VariableHelpItem(name: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryBlue, fontFamily = FontFamily.Monospace)
        Text(description, fontSize = 12.sp, color = TextLight)
    }
}

@Composable
fun SampleCodeBox(title: String, code: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextLight)
                TextButton(onClick = onCopy, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) {
                    Text("Copiar", fontSize = 11.sp, color = PrimaryBlue)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF81D4FA))
        }
    }
}

// --- APP PICKER DIALOG ---
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
                AppItemInfo(
                    label = pm.getApplicationLabel(app).toString(),
                    packageName = app.packageName
                )
            }.sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                installedApps.clear()
                installedApps.addAll(list)
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, installedApps.toList()) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Text("Seleccionar Aplicaciones", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar aplicación o paquete...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrimaryBlue)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Cargando aplicaciones...", color = TextGray, fontSize = 13.sp)
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se encontraron aplicaciones.", color = TextGray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isChecked = tempSelected.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) {
                                            tempSelected.remove(app.packageName)
                                        } else {
                                            tempSelected.add(app.packageName)
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            tempSelected.add(app.packageName)
                                        } else {
                                            tempSelected.remove(app.packageName)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextLight)
                                    Text(app.packageName, fontSize = 12.sp, color = TextGray)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = TextGray) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(tempSelected.toSet()) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Aceptar (${tempSelected.size})")
                    }
                }
            }
        }
    }
}
