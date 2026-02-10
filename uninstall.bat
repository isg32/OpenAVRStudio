@echo off
set APP_NAME=OpenAVRStudio
set INSTALL_DIR=%LOCALAPPDATA%\%APP_NAME%

echo 🗑 Uninstalling %APP_NAME%...

if exist "%USERPROFILE%\Desktop\OpenAVR Studio.lnk" del "%USERPROFILE%\Desktop\OpenAVR Studio.lnk"
if exist "%INSTALL_DIR%" rmdir /s /q "%INSTALL_DIR%"

echo ✅ Uninstallation Complete.
pause
