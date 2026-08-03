package app.casz.notifybridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receptor para autoinicio al reiniciar el dispositivo (BOOT_COMPLETED).
 * Garantiza que NotifyBridge esté listo en segundo plano tras el encendido.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action ||
            "android.intent.action.QUICKBOOT_POWERON" == action ||
            "com.htc.intent.action.QUICKBOOT_POWERON" == action
        ) {
            Log.d("BootReceiver", "Dispositivo iniciado. NotifyBridge listo en segundo plano.")
            // WorkManager y NotificationListenerService se auto-revisan al recibir notificaciones
        }
    }
}
