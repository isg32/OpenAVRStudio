@echo off
setlocal enabledelayedexpansion
set APP_NAME=OpenAVRStudio
set INSTALL_DIR=%LOCALAPPDATA%\%APP_NAME%
set AVR_ZIP_URL=https://downloads.arduino.cc/tools/avr-gcc-7.3.0-atmel3.6.1-arduino7-i686-w64-mingw32.zip

echo --------------------------------------------------
echo   %APP_NAME% Installer for Windows
echo --------------------------------------------------

:: 1. Check for Java
javac -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java JDK (javac) not found. Please install JDK first.
    pause
    exit /b
)

:: 2. Setup Folder
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

:: 3. Download and Install AVR Toolchain (if not present)
if not exist "%INSTALL_DIR%\avr-bin\bin\avr-gcc.exe" (
    echo [1/4] Downloading AVR Toolchain (this may take a minute)...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%AVR_ZIP_URL%' -OutFile '%INSTALL_DIR%\avr.zip'"
    
    echo [2/4] Extracting Toolchain...
    powershell -Command "Expand-Archive -Path '%INSTALL_DIR%\avr.zip' -DestinationPath '%INSTALL_DIR%\avr-temp' -Force"
    
    :: Move internal folder to a cleaner path
    move "%INSTALL_DIR%\avr-temp\avr" "%INSTALL_DIR%\avr-bin"
    rmdir /s /q "%INSTALL_DIR%\avr-temp"
    del "%INSTALL_DIR%\avr.zip"
) else (
    echo [1/4] AVR Toolchain already installed.
)

:: 4. Add to PATH (Permanent for User)
echo [3/4] Configuring Environment Variables...
setx PATH "%PATH%;%INSTALL_DIR%\avr-bin\bin"

:: 5. Compile App
echo [4/4] Compiling Application...
copy "%~dp0OpenAVRStudio.java" "%INSTALL_DIR%\"
javac -d "%INSTALL_DIR%" "%INSTALL_DIR%\OpenAVRStudio.java"

:: 6. Create Shortcut
set SCRIPT="%TEMP%\%RANDOM%-%RANDOM%-%RANDOM%-%RANDOM%.vbs"
echo Set oWS = WScript.CreateObject("WScript.Shell") >> %SCRIPT%
echo sLinkFile = oWS.SpecialFolders("Desktop") ^& "\OpenAVR Studio.lnk" >> %SCRIPT%
echo Set oLink = oWS.CreateShortcut(sLinkFile) >> %SCRIPT%
echo oLink.TargetPath = "javaw.exe" >> %SCRIPT%
echo oLink.Arguments = "-cp ""%INSTALL_DIR%"" OpenAVRStudio" >> %SCRIPT%
echo oLink.WorkingDirectory = "%INSTALL_DIR%" >> %SCRIPT%
echo oLink.Description = "OpenAVR Studio" >> %SCRIPT%
echo oLink.Save >> %SCRIPT%
cscript /nologo %SCRIPT%
del %SCRIPT%

echo.
echo 🎉 SUCCESS! OpenAVR Studio is installed.
echo NOTE: You may need to RESTART your Terminal or Log Out/In for the 'avr-gcc' command to be recognized.
pause
