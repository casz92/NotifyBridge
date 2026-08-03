# NotifyBridge

NotifyBridge es una aplicación nativa de Android diseñada para interceptar y retransmitir notificaciones Push y mensajes SMS hacia un servidor HTTP(S) externo en base a condiciones RegEx personalizables.

## Características Principales

1. **Dashboard de Reglas**: Permite definir criterios de origen (SMS o aplicaciones específicas), patrones RegEx y payloads HTTP de destino.
2. **Cola de Envíos (Queue)**: Muestra el estado de las peticiones salientes (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`, `CANCELLED`) con soporte para reintentos y cancelaciones desde la interfaz gráfica.
3. **Configuración Global**: Ajustes globales de Timeout, límite de reintentos y mapa de variables globales (por ejemplo, tokens API).

## Stack Tecnológico

- **Lenguaje**: Kotlin
- **Interfaz**: Jetpack Compose (Material 3)
- **Segundo Plano**: `WorkManager` & `NotificationListenerService`
- **Base de Datos**: `Room`
- **Preferencia Global**: `DataStore` (Preferences)
- **Cliente de Red**: `OkHttp`

---

## Instrucciones de Compilación y Ejecución

### Opción 1: Compilación por Comando (Consola)

Asegúrate de contar con el Java Development Kit (JDK 17) configurado en tus variables de entorno.

Desde la carpeta raíz del proyecto, ejecuta en la consola de comandos de Windows (cmd):

```cmd
gradlew.bat assembleDebug
```

Si deseas ejecutar pruebas o limpiar el proyecto:

*   **Limpiar compilaciones previas:**
    ```cmd
    gradlew.bat clean
    ```
*   **Compilar y generar bundle:**
    ```cmd
    gradlew.bat bundleDebug
    ```

### Opción 2: Compilación y Ejecución desde Android Studio

1.  Abre **Android Studio**.
2.  Selecciona **File > Open** y elige el directorio `C:\Users\suago\Documents\projects\NotifyBridge`.
3.  Espera a que Gradle sincronice las dependencias del proyecto.
4.  Conecta tu dispositivo físico Android mediante depuración USB o inicia un emulador.
5.  Haz clic en el botón verde de **Run** (ícono de play `▶`) en la barra de herramientas superior de Android Studio o presiona `Shift + F10`.
