@echo off
REM Build the current HDFView source and launch the resulting JAR.
REM Usage: build-and-run-hdfview.bat [--debug]

setlocal
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"
if errorlevel 1 goto :project_directory_error

if not "%~2"=="" goto :usage_error
set "LAUNCH_ARGS="
if not "%~1"=="" (
    if /i not "%~1"=="--debug" goto :usage_error
    set "LAUNCH_ARGS=--debug"
)

where mvn >nul 2>&1
if errorlevel 1 goto :maven_error

echo.
echo === Build HDFView from the current source ===
echo Command: mvn -pl hdfview -am package -DskipTests -B
echo.
call mvn -pl hdfview -am package -DskipTests -B
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" goto :build_error

echo.
echo === Launch the freshly built HDFView ===
call "%SCRIPT_DIR%run-hdfview.bat" %LAUNCH_ARGS%
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" goto :launch_error

endlocal & exit /b 0

:usage_error
echo [ERROR] Usage: %~nx0 [--debug]
set "EXIT_CODE=2"
goto :failure

:project_directory_error
echo [ERROR] Could not switch to the script directory: "%SCRIPT_DIR%"
set "EXIT_CODE=1"
goto :failure

:maven_error
echo [ERROR] Maven was not found in PATH. Install Maven 3.6 or later and try again.
set "EXIT_CODE=1"
goto :failure

:build_error
echo [ERROR] Maven build failed with exit code %EXIT_CODE%. HDFView was not launched.
goto :failure

:launch_error
echo [ERROR] HDFView launcher failed with exit code %EXIT_CODE%.
goto :failure

:failure
if /i not "%HDFVIEW_NO_PAUSE%"=="1" pause
endlocal & exit /b %EXIT_CODE%
