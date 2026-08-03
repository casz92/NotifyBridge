# Compilación y Ejecución de NotifyBridge (Debug, Release & Run)

Este documento detalla los pasos para construir e instalar la aplicación NotifyBridge tanto en modo depuración (**Debug**) como en producción (**Release**), además de la opción de ejecución directa (**Run**).

---

## 1. Ejecución Rápida en Dispositivo / Emulador (`run.bat`)

Para compilar, instalar e iniciar automáticamente la aplicación en un dispositivo o emulador conectado, utiliza el script **`run.bat`**:

### Ejecutar desde Consola (CMD):
```cmd
run.bat
```

### ¿Qué realiza `run.bat`?
1. Detecta automáticamente `adb` en el PATH o en la ruta del SDK de Android.
2. Verifica los dispositivos o emuladores activos (`adb devices`).
3. Compila e instala la APK mediante `app\gradlew.bat :app:installDebug`.
4. Inicia la actividad principal `MainActivity` en la pantalla del dispositivo.

---

## 2. Compilación de APKs mediante Script Interactivo (`build.bat`)

Para generar las APKs de instalación directamente en la raíz del proyecto, ejecuta:

```cmd
build.bat
```

El menú interactivo te permitirá elegir:
- **[1] Compilar APK Debug**: Genera `app-debug.apk` y lo copia a la raíz.
- **[2] Compilar APK Release**: Genera `app-release.apk` (requiere firma).
- **[3] Limpiar y Compilar Todo (Clean & Build)**.

---

## 3. Compilación por Comandos Gradle

Si prefieres usar la consola de comandos directamente con Gradle:

### Compilación Debug:
```cmd
app\gradlew.bat :app:assembleDebug
```
Ubicación de salida: `app/build/outputs/apk/debug/app-debug.apk`

### Instalar e Iniciar en Dispositivo:
```cmd
app\gradlew.bat :app:installDebug
adb shell am start -n app.casz.notifybridge/.ui.MainActivity
```

### Compilación Release (Producción):
```cmd
app\gradlew.bat :app:assembleRelease
```
Ubicación de salida: `app/build/outputs/apk/release/app-release.apk`

---

## 4. Compilación desde Android Studio

1. Abre **Android Studio**.
2. Selecciona **File > Open** y abre el directorio raíz del proyecto.
3. Espera a que la sincronización de Gradle finalice.
4. Conecta tu teléfono por USB (con Depuración USB activa) o inicia un emulador.
5. Presiona el botón de **Run** (ícono `▶` play) o `Shift + F10`.
