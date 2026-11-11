@echo off
title comparIT Server
echo ========================================
echo   Starting comparIT Server...
echo   Database: MySQL (localhost:3306)
echo ========================================

cd /d "%~dp0"

echo [1/2] Compiling Java files...
if not exist "out" mkdir out
javac -cp "lib\*" -d out src\grocery\*.java src\grocery\model\*.java src\grocery\service\*.java
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Compilation failed!
    echo Please make sure Java JDK 17+ is installed and added to PATH.
    echo.
    pause
    exit /b %errorlevel%
)

echo [2/2] Launching server at http://localhost:8080 ...
echo (Make sure MySQL is running on localhost:3306 before starting)
echo (Press Ctrl+C in this window to stop the server)
echo.

start http://localhost:8080

java -cp "out;lib\*" grocery.Main
pause
