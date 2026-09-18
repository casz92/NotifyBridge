package app.casz.notifybridge.util

import android.content.Context
import android.os.Build
import java.util.UUID

object DeviceUtil {
    private var cachedUuid: String? = null

    /**
     * Obtiene o genera un UUID de dispositivo único y persistente.
     */
    fun getDeviceUuid(context: Context): String {
        cachedUuid?.let { return it }

        val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        var uuid = prefs.getString("device_uuid", null)

        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString("device_uuid", uuid).apply()
        }

        cachedUuid = uuid
        return uuid
    }

    /**
     * Retorna la versión del kernel leyendo la propiedad del sistema.
     */
    fun getKernelVersion(): String {
        return System.getProperty("os.version") ?: "Unknown Kernel"
    }

    /**
     * Retorna la versión de la app NotifyBridge.
     */
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    /**
     * Retorna un mapa con todos los datos relevantes del sistema para los headers.
     */
    fun getSystemInfoHeaders(context: Context): Map<String, String> {
        val appVersion = getAppVersion(context)
        val osVersion = Build.VERSION.RELEASE
        val sdkVersion = Build.VERSION.SDK_INT.toString()
        val kernelVersion = getKernelVersion()
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val deviceUuid = getDeviceUuid(context)

        return mapOf(
            "X-NotifyBridge-Version" to appVersion,
            "X-NotifyBridge-OS" to "Android",
            "X-NotifyBridge-OS-Version" to osVersion,
            "X-NotifyBridge-SDK-Version" to sdkVersion,
            "X-NotifyBridge-Kernel" to kernelVersion,
            "X-NotifyBridge-Device-Manufacturer" to manufacturer,
            "X-NotifyBridge-Device-Model" to model,
            "X-NotifyBridge-Device-UUID" to deviceUuid
        )
    }
}
