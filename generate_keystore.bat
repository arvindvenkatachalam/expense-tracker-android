@echo off
REM Script to generate release keystore for Expense Tracker app

echo ========================================
echo Expense Tracker - Release Keystore Setup
echo ========================================
echo.

REM Try to find keytool in common Java locations
set KEYTOOL_PATH=

if exist "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" (
    set KEYTOOL_PATH=C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe
    echo Found keytool in Android Studio JBR
) else if exist "C:\Program Files\Java\jdk-17\bin\keytool.exe" (
    set KEYTOOL_PATH=C:\Program Files\Java\jdk-17\bin\keytool.exe
    echo Found keytool in Java JDK 17
) else if exist "C:\Program Files\Java\jdk-11\bin\keytool.exe" (
    set KEYTOOL_PATH=C:\Program Files\Java\jdk-11\bin\keytool.exe
    echo Found keytool in Java JDK 11
) else (
    echo ERROR: Could not find keytool.exe
    echo Please install Java JDK or Android Studio
    echo Or manually locate keytool.exe and update this script
    pause
    exit /b 1
)

echo.
echo Generating release keystore...
echo.
echo You will be prompted for:
echo 1. Keystore password (SAVE THIS!)
echo 2. Key password (can be same as keystore password)
echo 3. Your name
echo 4. Other details (you can skip by pressing Enter)
echo.

"%KEYTOOL_PATH%" -genkey -v -keystore app\release-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias expense-tracker-key

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo SUCCESS! Keystore created at: app\release-keystore.jks
    echo ========================================
    echo.
    echo NEXT STEPS:
    echo 1. Create keystore.properties file in project root
    echo 2. Add your passwords to keystore.properties
    echo 3. Run configure_signing.bat to update build files
    echo.
) else (
    echo.
    echo ERROR: Failed to create keystore
    echo.
)

pause
