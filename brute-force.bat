@echo off
REM FlyWay Checksum Brute-Force Tool
REM Usage: brute-force.bat <original.sql> [modified.sql] [--verbose]

set SCRIPT_DIR=%~dp0
set JAR_FILE=%SCRIPT_DIR%build\libs\brute-force-1.0-SNAPSHOT.jar

if not exist "%JAR_FILE%" (
    echo Error: JAR file not found. Please run: gradlew.bat build
    exit /b 1
)

java --add-opens java.base/java.util.zip=ALL-UNNAMED -jar "%JAR_FILE%" %*

