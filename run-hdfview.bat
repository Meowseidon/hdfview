@echo off
REM =============================================================================
REM HDFView Launcher Script for Windows
REM
REM This script validates the environment and launches an already-built HDFView.
REM Build the project first using: mvn -pl hdfview -am package -DskipTests -B
REM
REM Launch options:
REM   1. Maven exec:java (for development)
REM   2. Direct JAR execution (recommended)
REM
REM Requirements:
REM   - Java 21+
REM   - Maven 3.6+ (only for --maven)
REM   - HDF5 and HDF4 native libraries (configured in build.properties)
REM   - HDFView must be built before running this script
REM =============================================================================

setlocal enabledelayedexpansion

REM Script configuration
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

echo.
echo === HDFView Environment Check ^& Launcher ===
echo.

REM =============================================================================
REM Function to load properties from build.properties
REM =============================================================================
set "PROPS_FILE=build.properties"
if not exist "%PROPS_FILE%" (
    echo [ERROR] build.properties file not found!
    exit /b 1
)

echo [INFO] Loading build.properties...

REM Parse build.properties file
for /f "usebackq tokens=1,* delims==" %%a in ("%PROPS_FILE%") do (
    set "line=%%a"
    set "value=%%b"

    REM Skip empty lines and comments
    if not "!line!"=="" (
        echo !line! | findstr /r "^#" >nul
        if errorlevel 1 (
            REM Replace dots with underscores for variable names
            set "key=!line:.=_!"
            set "!key!=!value!"
        )
    )
)

echo [OK] build.properties loaded
echo.

REM =============================================================================
REM Environment Validation
REM =============================================================================

echo [INFO] Checking project structure...
if not exist "pom.xml" (
    echo [ERROR] Not in HDFView project root directory
    exit /b 1
)
if not exist "%PROPS_FILE%" (
    echo [ERROR] build.properties not found
    exit /b 1
)
echo [OK] Found project files
echo.

REM =============================================================================
REM Parse command line arguments before mode-specific checks
REM =============================================================================
set "SLF4J_IMPL=nop"
set "LAUNCH_MODE=jar"

:parse_args
if "%~1"=="" goto :args_done
if /i "%~1"=="--debug" (
    set "SLF4J_IMPL=simple"
    shift
    goto :parse_args
)
if /i "%~1"=="--choose" (
    set "LAUNCH_MODE=choose"
    shift
    goto :parse_args
)
if /i "%~1"=="--maven" (
    set "LAUNCH_MODE=maven"
    shift
    goto :parse_args
)
if /i "%~1"=="--validate" (
    set "LAUNCH_MODE=validate"
    shift
    goto :parse_args
)
shift
goto :parse_args

:args_done

REM Check Java
echo [INFO] Checking Java version...
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found in PATH
    echo [ERROR] Please install Java 21 or later
    exit /b 1
)

REM Get and parse Java version. Java 8-style versions such as 1.8.0_XXX
REM are normalized to major 8; modern versions such as 21.0.12 use 21.
set "JAVA_VERSION="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i /c:"version"') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~v"
if not defined JAVA_VERSION (
    echo [ERROR] Could not parse the Java version reported by java -version
    echo [ERROR] Java 21 or later is required
    exit /b 1
)
set "JAVA_MAJOR="
set "JAVA_MINOR="
for /f "tokens=1,2 delims=.-_" %%a in ("!JAVA_VERSION!") do (
    set "JAVA_MAJOR=%%a"
    set "JAVA_MINOR=%%b"
)
if "!JAVA_MAJOR!"=="1" set "JAVA_MAJOR=!JAVA_MINOR!"
if not defined JAVA_MAJOR (
    echo [ERROR] Could not parse the Java major version from !JAVA_VERSION!
    echo [ERROR] Java 21 or later is required
    exit /b 1
)
set "JAVA_MAJOR_INVALID="
for /f "delims=0123456789" %%x in ("!JAVA_MAJOR!") do set "JAVA_MAJOR_INVALID=1"
if defined JAVA_MAJOR_INVALID (
    echo [ERROR] Could not parse the Java major version from !JAVA_VERSION!
    echo [ERROR] Java 21 or later is required
    exit /b 1
)
set /a JAVA_MAJOR_NUM=!JAVA_MAJOR! >nul 2>&1
if !JAVA_MAJOR_NUM! LSS 21 (
    echo [ERROR] Java !JAVA_VERSION! detected ^(major !JAVA_MAJOR_NUM!^); Java 21 or later is required
    exit /b 1
)
echo [OK] Java !JAVA_VERSION! detected ^(major !JAVA_MAJOR_NUM!^; Java 21+ requirement satisfied^)
echo.

REM Check HDF5 libraries
echo [INFO] Checking HDF5 libraries...
if "!hdf5_lib_dir!"=="" (
    echo [ERROR] hdf5.lib.dir not configured in build.properties
    exit /b 1
)
if not exist "!hdf5_lib_dir!" (
    echo [ERROR] HDF5 library directory not found: !hdf5_lib_dir!
    echo [ERROR] Set hdf5.lib.dir in build.properties
    exit /b 1
)
echo [OK] HDF5 library directory found: !hdf5_lib_dir!

if not "!hdf5_plugin_dir!"=="" (
    if exist "!hdf5_plugin_dir!" (
        echo [OK] HDF5 plugin directory found: !hdf5_plugin_dir!
    ) else (
        echo [WARN] HDF5 plugin directory not found: !hdf5_plugin_dir!
    )
)
echo.

REM Check HDF4 libraries (optional)
echo [INFO] Checking HDF4 libraries (optional)...
if not "!hdf_lib_dir!"=="" (
    if exist "!hdf_lib_dir!" (
        echo [OK] HDF4 library directory found: !hdf_lib_dir!
    ) else (
        echo [WARN] HDF4 library directory not found: !hdf_lib_dir!
    )
) else (
    echo [INFO] HDF4 support not configured (optional)
)
echo.

REM Resolve an interactive choice before checking mode-specific prerequisites.
if /i "!LAUNCH_MODE!"=="choose" (
    echo Choose launch method:
    echo 1. Maven exec:java
    echo 2. Direct JAR execution ^(recommended^)
    echo 3. Validate direct JAR launch environment ^(no launch^)
    echo.
    set /p CHOICE="Enter choice [1-3]: "

    if "!CHOICE!"=="1" set "LAUNCH_MODE=maven"
    if "!CHOICE!"=="2" set "LAUNCH_MODE=jar"
    if "!CHOICE!"=="3" set "LAUNCH_MODE=validate"
    if not "!CHOICE!"=="1" if not "!CHOICE!"=="2" if not "!CHOICE!"=="3" (
        echo [ERROR] Invalid choice. Exiting.
        exit /b 1
    )
    echo.
)

REM Check build status
echo [INFO] Checking build status...
if /i "!LAUNCH_MODE!"=="maven" (
    echo [INFO] Direct JAR artifact check skipped for --maven mode.
    goto :skip_build_status
)
set "HDFVIEW_JAR="
for %%f in (libs\hdfview-*.jar) do (
    set "jarname=%%~nxf"
    echo !jarname! | findstr /i "sources javadoc" >nul
    if errorlevel 1 (
        set "HDFVIEW_JAR=%%f"
        goto :jar_found
    )
)
:jar_found
if "!HDFVIEW_JAR!"=="" (
    echo [ERROR] HDFView JAR not found: libs\hdfview-*.jar
    echo [ERROR] Build the project first: mvn -pl hdfview -am package -DskipTests -B
    exit /b 1
)
for %%f in (!HDFVIEW_JAR!) do set "HDFVIEW_JAR_NAME=%%~nxf"
echo [OK] HDFView JAR found: !HDFVIEW_JAR_NAME!
if not exist "hdfview\target\lib" (
    echo [ERROR] Dependencies not found: hdfview\target\lib
    echo [ERROR] Build the project first: mvn -pl hdfview -am package -DskipTests -B
    exit /b 1
)
echo [OK] HDFView runtime dependencies found: hdfview\target\lib
echo.
:skip_build_status

REM Check platform
echo [INFO] Checking SWT platform support...
echo [OK] Windows platform detected - SWT support available
echo.

echo [INFO] Environment validation complete!
echo.

REM Set up runtime environment
set "PATH=!platform_hdf_lib!;!PATH!"
set "SWT_LIBRARY_PATH=!SCRIPT_DIR!hdfview\target\native"
if not exist "!SWT_LIBRARY_PATH!" mkdir "!SWT_LIBRARY_PATH!" >nul 2>&1
if not "!hdf5_plugin_dir!"=="" (
    set "HDF5_PLUGIN_PATH=!hdf5_plugin_dir!"
)

REM JVM arguments for proper module access
set JVM_ARGS=--add-opens java.base/java.lang=ALL-UNNAMED
set JVM_ARGS=%JVM_ARGS% --add-opens java.base/java.time=ALL-UNNAMED
set JVM_ARGS=%JVM_ARGS% --add-opens java.base/java.time.format=ALL-UNNAMED
set JVM_ARGS=%JVM_ARGS% --add-opens java.base/java.util=ALL-UNNAMED
set JVM_ARGS=%JVM_ARGS% --enable-native-access=jarhdf5
set JVM_ARGS=%JVM_ARGS% "-Djava.library.path=!platform_hdf_lib!"
set JVM_ARGS=%JVM_ARGS% "-Dswt.library.path=!SWT_LIBRARY_PATH!"

REM Check environment variable for debug
if "%HDFVIEW_DEBUG%"=="1" set SLF4J_IMPL=simple

if "!SLF4J_IMPL!"=="simple" (
    echo [INFO] Debug logging enabled (slf4j-simple^)
) else (
    echo [INFO] Logging disabled (slf4j-nop^). Use --debug or set HDFVIEW_DEBUG=1 to enable.
)
echo.

REM Map launch mode to target
if /i "!LAUNCH_MODE!"=="maven" goto :maven_exec
if /i "!LAUNCH_MODE!"=="jar" goto :jar_exec
if /i "!LAUNCH_MODE!"=="validate" goto :validate

:maven_exec
echo [INFO] Checking Maven for --maven mode...
call mvn -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven not found in PATH
    echo [ERROR] Please install Maven 3.6 or later for --maven mode
    exit /b 1
)
set "MVN_VERSION="
for /f "tokens=3" %%v in ('call mvn -version 2^>^&1 ^| findstr /i /c:"Apache Maven"') do if not defined MVN_VERSION set "MVN_VERSION=%%v"
echo [OK] Maven !MVN_VERSION! found
echo.
echo [INFO] Launching HDFView via Maven...
echo Command: mvn exec:java -Dexec.mainClass="hdf.view.HDFView" -pl hdfview
echo.
call mvn exec:java -Dexec.mainClass="hdf.view.HDFView" -pl hdfview
goto :end

:jar_exec
echo [INFO] Launching HDFView via direct JAR execution...
if "!HDFVIEW_JAR!"=="" (
    echo [ERROR] JAR file not found. Build the project first.
    exit /b 1
)

if not exist "hdfview\target\lib" (
    echo [ERROR] Dependencies not found: hdfview\target\lib
    echo [ERROR] Build the project first: mvn -pl hdfview -am package -DskipTests -B
    exit /b 1
)

REM Build classpath, excluding slf4j-nop or slf4j-simple based on debug mode
set CLASSPATH=!HDFVIEW_JAR!
for %%j in (hdfview\target\lib\*.jar) do (
    set "jarname=%%~nxj"
    if "!SLF4J_IMPL!"=="simple" (
        REM Skip nop, include simple
        echo !jarname! | findstr /i "^slf4j-nop" >nul
        if errorlevel 1 set "CLASSPATH=!CLASSPATH!;%%j"
    ) else (
        REM Skip simple, include nop
        echo !jarname! | findstr /i "^slf4j-simple" >nul
        if errorlevel 1 set "CLASSPATH=!CLASSPATH!;%%j"
    )
)

echo Command: java %JVM_ARGS% -cp "..." hdf.view.HDFView
echo.
java %JVM_ARGS% -cp "%CLASSPATH%" hdf.view.HDFView
goto :end

:validate
echo [OK] Direct JAR launch validation complete.
echo [INFO] Checked Java 21+, configured HDF native library paths, built JAR, and runtime dependencies.
echo [INFO] Maven was not checked and no build was run. Use --maven to check Maven and launch through Maven.
echo.
echo To launch manually:
echo Option 1 (JAR^): java %JVM_ARGS% -cp "!HDFVIEW_JAR!;hdfview\target\lib\*" hdf.view.HDFView
echo Option 2 (Maven^): run-hdfview.bat --maven
goto :end

:end
echo.
echo [OK] Script completed!
endlocal
