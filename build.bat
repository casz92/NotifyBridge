@echo off
title NotifyBridge Builder
setlocal enabledelayedexpansion

echo ===================================================
echo             NotifyBridge APK Builder
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

echo Seleccione la opcion de compilacion:
echo [1] Compilar APK Debug (Para desarrollo)
echo [2] Compilar APK Release (Optimizado)
echo [3] Limpiar proyecto (Clean)
echo.
set /p OPCION="Ingrese opcion [1-3]: "

if "%OPCION%"=="1" goto build_debug
if "%OPCION%"=="2" goto build_release
if "%OPCION%"=="3" goto clean_only
echo Opcion no valida. Saliendo...
goto end

:build_debug
echo.
echo === INICIANDO COMPILACION DEBUG ===
echo Ejecutando gradlew assembleDebug...
call app\gradlew.bat :app:assembleDebug
if %ERRORLEVEL% neq 0 goto error

echo.
echo Copiando APK a la raiz del proyecto...
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    copy "app\build\outputs\apk\debug\app-debug.apk" "NotifyBridge-debug.apk" > nul
    echo ===================================================
    echo  ¡PROCESO COMPLETADO CON EXITO!
    echo  APK: %~dp0NotifyBridge-debug.apk
    echo ===================================================
    explorer.exe /select,"%~dp0NotifyBridge-debug.apk"
) else (
    echo [ERROR] No se pudo encontrar el archivo APK de salida.
)
goto end

:build_release
echo.
echo === INICIANDO COMPILACION RELEASE ===
echo Ejecutando gradlew assembleRelease...
call app\gradlew.bat :app:assembleRelease
if %ERRORLEVEL% neq 0 goto error

echo.
echo Copiando APK a la raiz del proyecto...
if exist "app\build\outputs\apk\release\app-release-unsigned.apk" (
    copy "app\build\outputs\apk\release\app-release-unsigned.apk" "NotifyBridge-release-unsigned.apk" > nul
    echo ===================================================
    echo  ¡PROCESO COMPLETADO CON EXITO!
    echo  APK: %~dp0NotifyBridge-release-unsigned.apk
    echo ===================================================
    explorer.exe /select,"%~dp0NotifyBridge-release-unsigned.apk"
) else if exist "app\build\outputs\apk\release\app-release.apk" (
    copy "app\build\outputs\apk\release\app-release.apk" "NotifyBridge-release.apk" > nul
    echo ===================================================
    echo  ¡PROCESO COMPLETADO CON EXITO!
    echo  APK: %~dp0NotifyBridge-release.apk
    echo ===================================================
    explorer.exe /select,"%~dp0NotifyBridge-release.apk"
) else (
    echo [ERROR] No se pudo encontrar el archivo APK de salida.
)
goto end

:clean_only
echo.
echo === LIMPIANDO PROYECTO ===
call app\gradlew.bat clean
if %ERRORLEVEL% neq 0 goto error
echo Proyecto limpio con exito.
goto end

:error
echo.
echo ===================================================
echo  [ERROR] Ocurrio un fallo durante el proceso.
echo ===================================================
goto end

:end
echo.
pause
