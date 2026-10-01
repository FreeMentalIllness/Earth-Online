@echo off
setlocal
cd /d "%~dp0"

if not exist keystore mkdir keystore
if exist keystore\eo-release-key.jks del /f keystore\eo-release-key.jks

set /p STOREPASS=Enter keystore password (visible as you type): 

set "KEYTOOL="
where keytool >nul 2>nul && set "KEYTOOL=keytool"
if not defined KEYTOOL if exist "%JAVA_HOME%\bin\keytool.exe" set "KEYTOOL=%JAVA_HOME%\bin\keytool.exe"
if not defined KEYTOOL if exist "D:\JAVA\bin\keytool.exe" set "KEYTOOL=D:\JAVA\bin\keytool.exe"
if not defined KEYTOOL if exist "D:\Android\Android Studio\jbr\bin\keytool.exe" set "KEYTOOL=D:\Android\Android Studio\jbr\bin\keytool.exe"
if not defined KEYTOOL if exist "D:\Android\Android studio\jbr\bin\keytool.exe" set "KEYTOOL=D:\Android\Android studio\jbr\bin\keytool.exe"

if not defined KEYTOOL (
  echo [ERROR] keytool not found on PATH or known JDK locations.
  echo         Run this from Android Studio Terminal, or install a JDK.
  pause
  exit /b 1
)

echo Using keytool: %KEYTOOL%
%KEYTOOL% -genkeypair -v -keystore keystore/eo-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias earthonline -storepass %STOREPASS% -keypass %STOREPASS% -dname "CN=EarthOnline, OU=EarthOnline, O=EarthOnline, C=CN"

if errorlevel 1 (
  echo [ERROR] keystore generation failed.
  pause
  exit /b 1
)

%KEYTOOL% -list -keystore keystore/eo-release-key.jks -storepass %STOREPASS% >nul 2>&1
if errorlevel 1 (
  echo [ERROR] keystore password verification failed!
  pause
  exit /b 1
)

if exist local.properties (
  findstr /v "EO_STORE_ EO_KEY_" local.properties > local.properties.tmp
  move /y local.properties.tmp local.properties >nul
)
echo EO_STORE_FILE=keystore/eo-release-key.jks>>local.properties
echo EO_STORE_PASSWORD=%STOREPASS%>>local.properties
echo EO_KEY_ALIAS=earthonline>>local.properties
echo EO_KEY_PASSWORD=%STOREPASS%>>local.properties

echo.
echo ============================================
echo  Keystore generated SUCCESSFULLY.
echo  BACK UP password and keystore/ folder NOW!
echo ============================================
echo.
pause
