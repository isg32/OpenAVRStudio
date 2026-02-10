@echo off
echo ==========================================
echo    OpenAVR Studio EXE Bundler
echo ==========================================

set APP_NAME=OpenAVRStudio
set BUILD_DIR=package_work
set INPUT_DIR=%BUILD_DIR%\input
set OUTPUT_DIR=dist

:: 1. Clean previous builds
if exist %BUILD_DIR% rmdir /s /q %BUILD_DIR%
if exist %OUTPUT_DIR% rmdir /s /q %OUTPUT_DIR%
mkdir %INPUT_DIR%
mkdir %OUTPUT_DIR%

:: 2. Compile the Java code into a JAR
echo [1/4] Compiling Java...
javac -d %INPUT_DIR% OpenAVRStudio.java
echo Main-Class: OpenAVRStudio > %BUILD_DIR%\manifest.txt
jar cfm %INPUT_DIR%\%APP_NAME%.jar %BUILD_DIR%\manifest.txt -C %INPUT_DIR% .
del %INPUT_DIR%\*.class

:: 3. Download and bundle AVR Toolchain into the input folder
echo [2/4] Bundling AVR Toolchain...
set AVR_URL=https://downloads.arduino.cc/tools/avr-gcc-7.3.0-atmel3.6.1-arduino7-i686-w64-mingw32.zip
powershell -Command "Invoke-WebRequest -Uri '%AVR_URL%' -OutFile '%BUILD_DIR%\avr.zip'"
powershell -Command "Expand-Archive -Path '%BUILD_DIR%\avr.zip' -DestinationPath '%BUILD_DIR%\avr-temp'"
move "%BUILD_DIR%\avr-temp\avr" "%INPUT_DIR%\avr-bin"

:: 4. Use jpackage to create the EXE
:: This bundles the JRE (Java), the JAR, and the AVR tools into one installer
echo [3/4] Creating Native EXE Installer...
jpackage ^
  --type exe ^
  --dest %OUTPUT_DIR% ^
  --name "OpenAVRStudio" ^
  --input %INPUT_DIR% ^
  --main-jar %APP_NAME%.jar ^
  --main-class OpenAVRStudio ^
  --win-shortcut ^
  --win-menu ^
  --icon "icon.ico" ^
  --vendor "isg32" ^
  --description "AVR Assembly IDE with built-in Toolchain"

echo [4/4] Cleaning up...
rmdir /s /q %BUILD_DIR%

echo ==========================================
echo  DONE! Look in the '%OUTPUT_DIR%' folder.
echo ==========================================
pause
