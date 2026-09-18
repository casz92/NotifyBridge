package app.casz.notifybridge.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.casz.notifybridge.data.local.entity.RuleEntity
import app.casz.notifybridge.ui.theme.AppColors
import app.casz.notifybridge.util.RuleSimulator
import app.casz.notifybridge.util.SimulationResult
import kotlinx.coroutines.launch

@Composable
fun SimulationDialog(rule: RuleEntity, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SimulationResult?>(null) }

    val resolvedBody = remember(rule) { RuleSimulator.resolveTemplate(rule.bodyTemplate, context) }
    val resolvedUrl  = remember(rule) { RuleSimulator.resolveTemplate(rule.httpUrl, context) }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        containerColor = AppColors.Surface,
        title = {
            Text("Probar Conexión", fontWeight = FontWeight.Bold, color = AppColors.Primary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Configuración de Envío:", fontWeight = FontWeight.Bold, color = AppColors.OnSurface, fontSize = 14.sp)
                Text("Método: ${rule.httpMethod}", fontSize = 13.sp, color = AppColors.OnSurfaceVar)
                Text("URL Destino: $resolvedUrl", fontSize = 13.sp, color = AppColors.OnSurfaceVar)

                Spacer(modifier = Modifier.height(4.dp))
                Text("Cuerpo (Simulado):", fontWeight = FontWeight.Bold, color = AppColors.OnSurface, fontSize = 14.sp)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceVariant)
                ) {
                    Text(
                        text = resolvedBody,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = AppColors.Primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (isSending) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Esperando respuesta...", color = AppColors.OnSurfaceVar, fontSize = 14.sp)
                    }
                }

                result?.let { res ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = AppColors.Divider)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Resultado del Envío:", fontWeight = FontWeight.Bold, color = AppColors.OnSurface, fontSize = 14.sp)
                    when (res) {
                        is SimulationResult.Success -> {
                            val isOk = res.code in 200..299
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Código de Respuesta: ", fontSize = 13.sp, color = AppColors.OnSurfaceVar)
                                Text(
                                    "${res.code}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOk) AppColors.StatusSuccess else AppColors.StatusFailed,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Cuerpo de Respuesta:", fontWeight = FontWeight.Bold, color = AppColors.OnSurface, fontSize = 13.sp)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceVariant)
                            ) {
                                Text(
                                    text = res.body.ifBlank { "(Respuesta vacía)" },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = AppColors.OnSurface,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        is SimulationResult.Failure -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = AppColors.StatusFailedBg)
                            ) {
                                Text(
                                    text = res.error,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.StatusFailed,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
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
                            coroutineScope.launch {
                                try {
                                    result = RuleSimulator.simulateSend(context, rule)
                                } catch (t: Throwable) {
                                    result = SimulationResult.Failure(t.localizedMessage ?: t.message ?: "Error desconocido")
                                } finally {
                                    isSending = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        Text("Enviar Simulación", color = AppColors.OnPrimary)
                    }
                } else if (!isSending) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        Text("Cerrar", color = AppColors.OnPrimary)
                    }
                }
            }
        },
        dismissButton = {
            if (result == null && !isSending) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = AppColors.OnSurfaceVar)
                }
            }
        }
    )
}
