@echo off
title NotifyBridge - Ejecutar en Dispositivo
setlocal enabledelayedexpansion

echo ===================================================
echo          NotifyBridge App Launcher / Runner
echo ===================================================
echo.

:: Ensure local.properties exists in root
if not exist "local.properties" (
    if exist "app\local.properties" (
        copy "app\local.properties" "local.properties" > nul
    ) else (
        echo [INFO] Creando local.properties por defecto...
        echo sdk.dir=%USERPROFILE%\AppData\Local\Android\Sdk > local.properties
    )
)

:: Locate adb.exe
set "ADB_PATH=adb"
where adb >nul 2>nul
if %ERRORLEVEL% neq 0 (
    if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
        set "ADB_PATH=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    ) else (
        echo [WARNING] No se encontro adb en el PATH ni en la ruta predeterminada del SDK.
    )
)

echo [1/3] Verificando dispositivos o emuladores conectados...
"%ADB_PATH%" devices
echo.

echo [2/3] Compilando e instalando APK en el dispositivo...
call app\gradlew.bat :app:installDebug

if %ERRORLEVEL% neq 0 (
    echo.
    echo ===================================================
    echo  [ERROR] Fallo la compilacion o instalacion.
    echo  Asegurate de tener un emulador encendido o un
    echo  telefono conectado con Depuracion USB activada.
    echo ===================================================
    goto end
)

echo.
echo [3/3] Iniciando NotifyBridge en el dispositivo...
"%ADB_PATH%" shell am start -n app.casz.notifybridge/.ui.MainActivity

if %ERRORLEVEL% eq 0 (
    echo.
    echo ===================================================
    echo  ¡NOTIFYBRIDGE INICIADO CON EXITO EN EL DISPOSITIVO!
    echo ===================================================
) else (
    echo [ERROR] No se pudo iniciar la actividad principal en el dispositivo.
)

:end
echo.
pause
