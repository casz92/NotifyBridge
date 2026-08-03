# NotifyBridge

NotifyBridge es una aplicación nativa de Android diseñada para interceptar y retransmitir notificaciones Push y mensajes SMS hacia un servidor HTTP(S) externo en base a condiciones y filtros RegEx personalizables.

## Características Principales

1. **Dashboard de Reglas (Crear, Editar, Borrar)**:
   - Permite definir criterios por fuente (**SMS**, **Apps instaladas** o **IMAP / Gmail**).
   - **Filtro Regex Dirigido (regexMatchFields)**: Permite dirigir la expresión regular a campos específicos (ej. Título/Texto en Apps, Remitente/Destinatario/Contenido en SMS, y Emisor/Receptor/Asunto/Cuerpo en IMAP).
   - Selector de aplicaciones con **cargador asíncrono** en hilo secundario.
   - Pestañas organizadas: **General**, **Headers** (con sugerencias de autocompletado) y **Body** (con soporte JSON / Texto).
   - Mapeo de variables dinámicas: `{not_title}`, `{sms_sender}` (remitente SMS), `{not_text}`, `{package_name}`, `{timestamp}`, `{imap_subject}`, `{imap_body}` y `{global_NOMBRE}`.
2. **Importación y Exportación de Reglas (JSON)**:
   - Exporta e importa copias de seguridad de las reglas en archivos `.json` de forma nativa con **control de versión** integrado (versión 1).
3. **Cola de Envíos Organizada (Tabs Activos e Historial)**:
   - **Activos**: Envíos pendientes (`PENDING`), en proceso (`PROCESSING`) o fallidos con reintento (`FAILED`).
   - **Historial**: Envíos completados (`SUCCESS`) o cancelados (`CANCELLED`), con opción de borrado individual y **"Limpiar Historial"**.
4. **Optimización de Batería y Autoinicio**:
   - Tarjeta dedicada en Ajustes para verificar el estado de la batería e invocar la exclusión de optimización de batería (`Doze mode`).
   - Receptor `BootReceiver` para autoinicio al encender o reiniciar el dispositivo (`BOOT_COMPLETED`).

## Stack Tecnológico

- **Lenguaje**: Kotlin (1.9.24)
- **Interfaz**: Jetpack Compose (Material 3)
- **Segundo Plano**: `WorkManager` & `NotificationListenerService` & `BootReceiver`
- **Base de Datos**: `Room`
- **Preferencia Global**: `DataStore` (Preferences)
- **Cliente de Red**: `OkHttp`

---

## Compilación y Ejecución Rápida

### Opción 1: Ejecución Directa en Dispositivo/Emulador (`run.bat`)
Ejecuta en la consola de comandos de Windows:
```cmd
run.bat
```
Este script compila la APK debug, detecta el dispositivo/emulador conectado vía `adb`, la instala e inicia automáticamente la aplicación.

### Opción 2: Compilación de APK (`build.bat`)
Ejecuta el script interactivo:
```cmd
build.bat
```
Permite seleccionar si deseas generar una versión **Debug** o **Release** y copia el resultado directamente a la raíz del proyecto.
