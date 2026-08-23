@echo off
setlocal
set GRADLE_VERSION=9.3.1
set SCRIPT_DIR=%~dp0
set DIST_ROOT=%SCRIPT_DIR%.gradle-dist
set DIST_DIR=%DIST_ROOT%\gradle-%GRADLE_VERSION%
set ZIP_FILE=%DIST_ROOT%\gradle-%GRADLE_VERSION%-bin.zip
set URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip

if exist "%DIST_DIR%\bin\gradle.bat" goto run
if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"
if not exist "%ZIP_FILE%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing '%URL%' -OutFile '%ZIP_FILE%'"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP_FILE%' '%DIST_ROOT%'"
if errorlevel 1 exit /b 1

:run
call "%DIST_DIR%\bin\gradle.bat" %*
