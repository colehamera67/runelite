@echo off
REM RuneLite Multiboxer Server Startup Script (Windows)

echo ========================================
echo  RuneLite Multiboxer Server
echo ========================================
echo.

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 11 or higher
    pause
    exit /b 1
)

REM Default port
set PORT=43594

REM Check for custom port argument
if not "%1"=="" (
    set PORT=%1
)

echo Starting server on port %PORT%...
echo.

REM Run the server
java -cp "../../../../../../../target/classes" net.runelite.client.plugins.multiboxer.server.MultiboxerServer %PORT%

pause
