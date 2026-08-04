package app.casz.notifybridge.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.util.RuleSimulator
import app.casz.notifybridge.util.SimulationResult
import kotlinx.coroutines.launch

@Composable
fun SimulationDialog(rule: RuleEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SimulationResult?>(null) }

    val resolvedBody = remember(rule) { RuleSimulator.resolveTemplate(rule.bodyTemplate, context) }
    val resolvedUrl = remember(rule) { RuleSimulator.resolveTemplate(rule.httpUrl, context) }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Probar Conexión", fontWeight = FontWeight.Bold, color = PrimaryBlue) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Configuración de Envío:", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 14.sp)
                Text("Método: ${rule.httpMethod}", fontSize = 13.sp, color = TextGray)
                Text("URL Destino: $resolvedUrl", fontSize = 13.sp, color = TextGray)
                
                Spacer(modifier = Modifier.height(4.dp))
                Text("Cuerpo (Simulado):", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 14.sp)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = resolvedBody,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF81D4FA),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                if (isSending) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Esperando respuesta...", color = TextLight, fontSize = 14.sp)
                    }
                }

                result?.let { res ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Resultado del Envío:", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 14.sp)
                    when (res) {
                        is SimulationResult.Success -> {
                            val codeColor = if (res.code in 200..299) Color(0xFF4CAF50) else Color.Red
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Código de Respuesta: ", fontSize = 13.sp, color = TextGray)
                                Text("${res.code}", fontWeight = FontWeight.Bold, color = codeColor, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Cuerpo de Respuesta:", fontWeight = FontWeight.Bold, color = TextLight, fontSize = 13.sp)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = res.body.ifBlank { "(Respuesta vacía)" },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextLight,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        is SimulationResult.Failure -> {
                            Text(
                                text = res.error,
                                fontWeight = FontWeight.Bold,
                                color = Color.Red,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (result == null && !isSending) {
                    Button(
                        onClick = {
                            isSending = true
                            android.util.Log.d("SimulationDialog", "Iniciando corrutina para simulación de envío.")
                            coroutineScope.launch {
                                try {
                                    result = RuleSimulator.simulateSend(context, rule)
                                    android.util.Log.d("SimulationDialog", "Simulación finalizada. Resultado: $result")
                                } catch (t: Throwable) {
                                    android.util.Log.e("SimulationDialog", "Error grave en corrutina de simulación", t)
                                    result = SimulationResult.Failure(t.localizedMessage ?: t.message ?: "Error desconocido")
                                } finally {
                                    isSending = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Enviar Simulación", color = Color.White)
                    }
                } else if (!isSending) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Cerrar", color = Color.White)
                    }
                }
            }
        },
        dismissButton = {
            if (result == null && !isSending) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = TextGray)
                }
            }
        },
        containerColor = CardBackground
    )
}
