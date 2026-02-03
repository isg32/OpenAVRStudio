#!/bin/bash

# Define variables
APP_NAME="OpenAVRStudio"
INSTALL_DIR="$HOME/.local/share/$APP_NAME"
DESKTOP_FILE="$HOME/.local/share/applications/$APP_NAME.desktop"
JAVA_SOURCE="OpenAVRStudio.java"

echo "🔧 Installing $APP_NAME..."

# 1. Create hidden install directory
mkdir -p "$INSTALL_DIR"

# 2. Copy source and compile inside the install dir
cp "$JAVA_SOURCE" "$INSTALL_DIR/"
echo "☕ Compiling Java..."
javac -d "$INSTALL_DIR" "$INSTALL_DIR/$JAVA_SOURCE"

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed. Make sure you have java-devel installed."
    exit 1
fi

# 3. Create a launch script
echo "#!/bin/bash
cd \"$INSTALL_DIR\"
java $APP_NAME
" > "$INSTALL_DIR/run.sh"

chmod +x "$INSTALL_DIR/run.sh"

# 4. Download a nice icon (using a generic chip icon from internet)
echo "🖼️  Fetching icon..."
curl -s -o "$INSTALL_DIR/icon.png" "https://raw.githubusercontent.com/isg32/OpenAVRStudio/master/assets/logo.png"

# 5. Create the .desktop file
echo "📝 Creating Desktop Shortcut..."
cat > "$DESKTOP_FILE" <<EOL
[Desktop Entry]
Version=1.0
Type=Application
Name=OpenAVR Studio
Comment=AVR Assembly IDE for Linux
Exec=$INSTALL_DIR/run.sh
Icon=$INSTALL_DIR/icon.png
Terminal=false
Categories=Development;IDE;Electronics;
StartupNotify=true
EOL

# 6. Refresh Gnome/DE
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null

echo "✅ Success! 'OpenAVR Studio' is now in your App Menu."
