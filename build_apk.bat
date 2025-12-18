@echo off
REM Battery Drainer APK Build Script for Windows
REM Builds debug and/or release APKs

setlocal enabledelayedexpansion

echo ========================================
echo    Battery Drainer APK Builder
echo ========================================
echo.

REM Default values
set BUILD_TYPE=debug
set CLEAN_BUILD=0
set INSTALL_APK=0

REM Parse arguments
:parse_args
if "%~1"=="" goto :done_parsing
if /i "%~1"=="-d" set BUILD_TYPE=debug& shift& goto :parse_args
if /i "%~1"=="--debug" set BUILD_TYPE=debug& shift& goto :parse_args
if /i "%~1"=="-r" set BUILD_TYPE=release& shift& goto :parse_args
if /i "%~1"=="--release" set BUILD_TYPE=release& shift& goto :parse_args
if /i "%~1"=="-a" set BUILD_TYPE=all& shift& goto :parse_args
if /i "%~1"=="--all" set BUILD_TYPE=all& shift& goto :parse_args
if /i "%~1"=="-c" set CLEAN_BUILD=1& shift& goto :parse_args
if /i "%~1"=="--clean" set CLEAN_BUILD=1& shift& goto :parse_args
if /i "%~1"=="-i" set INSTALL_APK=1& shift& goto :parse_args
if /i "%~1"=="--install" set INSTALL_APK=1& shift& goto :parse_args
if /i "%~1"=="-h" goto :show_help
if /i "%~1"=="--help" goto :show_help
echo Unknown option: %~1
goto :show_help

:show_help
echo Usage: build_apk.bat [OPTIONS]
echo.
echo Options:
echo   -d, --debug       Build debug APK (default)
echo   -r, --release     Build release APK
echo   -a, --all         Build both debug and release APKs
echo   -c, --clean       Clean before building
echo   -i, --install     Install APK on connected device
echo   -h, --help        Show this help message
echo.
echo Examples:
echo   build_apk.bat                 # Build debug APK
echo   build_apk.bat -r -c           # Clean build release APK
echo   build_apk.bat -d -i           # Build and install debug APK
exit /b 0

:done_parsing

REM Get script directory
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM Output directory
set OUTPUT_DIR=%SCRIPT_DIR%release-builds
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

REM Check for gradlew
if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found. Are you in the project root?
    exit /b 1
)

REM Check Java
echo Checking Java version...
java -version 2>&1 | findstr /i "version"
if errorlevel 1 (
    echo ERROR: Java not found. Please install JDK 17+
    exit /b 1
)

REM Clean if requested
if %CLEAN_BUILD%==1 (
    echo.
    echo Cleaning project...
    call gradlew.bat clean
    echo Clean complete!
)

REM Build based on type
if "%BUILD_TYPE%"=="debug" goto :build_debug
if "%BUILD_TYPE%"=="release" goto :build_release
if "%BUILD_TYPE%"=="all" goto :build_all
goto :build_debug

:build_debug
echo.
echo Building Debug APK...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo ERROR: Debug build failed!
    exit /b 1
)

set DEBUG_APK=app\build\outputs\apk\debug\app-debug.apk
if exist "%DEBUG_APK%" (
    for /f "tokens=1-3 delims=/ " %%a in ('date /t') do set DATESTAMP=%%c%%a%%b
    for /f "tokens=1-2 delims=: " %%a in ('time /t') do set TIMESTAMP=%%a%%b
    copy "%DEBUG_APK%" "%OUTPUT_DIR%\BatteryDrainer-debug-latest.apk" >nul
    echo.
    echo SUCCESS: Debug APK built!
    echo Location: %OUTPUT_DIR%\BatteryDrainer-debug-latest.apk
)

if "%BUILD_TYPE%"=="all" goto :build_release
goto :post_build

:build_release
echo.
echo Building Release APK...
call gradlew.bat assembleRelease
if errorlevel 1 (
    echo ERROR: Release build failed!
    exit /b 1
)

set RELEASE_APK=app\build\outputs\apk\release\app-release.apk
set RELEASE_UNSIGNED=app\build\outputs\apk\release\app-release-unsigned.apk

if exist "%RELEASE_APK%" (
    copy "%RELEASE_APK%" "%OUTPUT_DIR%\BatteryDrainer-release-latest.apk" >nul
    echo SUCCESS: Release APK built!
    echo Location: %OUTPUT_DIR%\BatteryDrainer-release-latest.apk
) else if exist "%RELEASE_UNSIGNED%" (
    copy "%RELEASE_UNSIGNED%" "%OUTPUT_DIR%\BatteryDrainer-release-unsigned-latest.apk" >nul
    echo WARNING: Unsigned Release APK built.
    echo Location: %OUTPUT_DIR%\BatteryDrainer-release-unsigned-latest.apk
    echo Note: Sign this APK before uploading to Play Store
)

echo.
echo Building App Bundle for Play Store...
call gradlew.bat bundleRelease
if errorlevel 1 (
    echo WARNING: Bundle build failed!
) else (
    set BUNDLE=app\build\outputs\bundle\release\app-release.aab
    if exist "!BUNDLE!" (
        copy "!BUNDLE!" "%OUTPUT_DIR%\BatteryDrainer-release-latest.aab" >nul
        echo SUCCESS: App Bundle built!
        echo Location: %OUTPUT_DIR%\BatteryDrainer-release-latest.aab
    )
)
goto :post_build

:build_all
goto :build_debug

:post_build

REM Install if requested
if %INSTALL_APK%==1 (
    echo.
    echo Installing APK on device...
    where adb >nul 2>&1
    if errorlevel 1 (
        echo ERROR: adb not found. Cannot install APK.
    ) else (
        if "%BUILD_TYPE%"=="release" (
            adb install -r "%OUTPUT_DIR%\BatteryDrainer-release-latest.apk"
        ) else (
            adb install -r "%OUTPUT_DIR%\BatteryDrainer-debug-latest.apk"
        )
    )
)

echo.
echo ========================================
echo    Build Complete!
echo ========================================
echo.
echo Output directory: %OUTPUT_DIR%
echo.
dir /b "%OUTPUT_DIR%\*.apk" "%OUTPUT_DIR%\*.aab" 2>nul

REM Open output folder
start "" "%OUTPUT_DIR%"

endlocal
