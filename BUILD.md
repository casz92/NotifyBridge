# Compilación de APKs (Debug & Release)

Este documento detalla los pasos para construir los archivos de instalación de Android (.apk) tanto de depuración (Debug) como de producción (Release).

---

## 1. Compilación de APK Debug (Depuración)

El APK Debug se utiliza para realizar pruebas rápidas y desarrollo. Está firmado automáticamente con una firma temporal de desarrollo ("debug.keystore").

### Opción A: Desde Línea de Comandos (Consola)
Ejecuta el siguiente comando en la raíz del proyecto:
```cmd
gradle assembleDebug
```
El archivo generado se ubicará en:
`app/build/outputs/apk/debug/app-debug.apk`

### Opción B: Desde Android Studio
1. Abre el proyecto en Android Studio.
2. Abre la pestaña de Gradle (en el panel derecho).
3. Navega a `NotifyBridge > app > Tasks > build > assembleDebug` y haz doble clic.
4. O en el menú de herramientas superior: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

---

## 2. Compilación de APK Release (Producción)

El APK Release está optimizado y preparado para distribución. Para instalarlo en dispositivos reales de usuarios finales, debe estar firmado con una llave de producción privada (`keystore`).

### Paso 1: Configurar KeyStore (Firma de Producción)
Si no dispones de un KeyStore, puedes generar uno mediante consola con `keytool` o desde Android Studio en **Build > Generate Signed Bundle / APK...**.

Luego, crea o edita el archivo `local.properties` en la raíz del proyecto para definir las credenciales (o inclúyelas directamente en tu bloque de firma de Gradle):
```properties
RELEASE_STORE_FILE=mi-llave-produccion.jks
RELEASE_STORE_PASSWORD=miContrasenaAlmacen
RELEASE_KEY_ALIAS=miAliasLlave
RELEASE_KEY_PASSWORD=miContrasenaLlave
```

### Paso 2: Compilación
Ejecuta el siguiente comando en la raíz del proyecto:
```cmd
gradle assembleRelease
```
El archivo firmado se ubicará en:
`app/build/outputs/apk/release/app-release.apk`

---

## 3. Automatización mediante Script Batch (.bat)

Para simplificar el flujo de trabajo en Windows, hemos incluido el script `build.bat` en la raíz del proyecto. Este archivo te permite compilar con un solo clic.
