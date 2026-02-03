#!/bin/bash

# Define variables (Must match the installer)
APP_NAME="OpenAVRStudio"
INSTALL_DIR="$HOME/.local/share/$APP_NAME"
DESKTOP_FILE="$HOME/.local/share/applications/$APP_NAME.desktop"

echo "🗑️  Uninstalling $APP_NAME..."

# 1. Remove the Desktop Entry
if [ -f "$DESKTOP_FILE" ]; then
    rm "$DESKTOP_FILE"
    echo "✅ Removed Desktop Shortcut"
else
    echo "⚠️  Desktop Shortcut not found (skipped)"
fi

# 2. Remove the Installation Directory
if [ -d "$INSTALL_DIR" ]; then
    rm -rf "$INSTALL_DIR"
    echo "✅ Removed Program Files ($INSTALL_DIR)"
else
    echo "⚠️  Program directory not found (skipped)"
fi

# 3. Refresh Gnome/DE database
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null

echo "🎉 Uninstallation Complete. OpenAVR Studio has been removed."
