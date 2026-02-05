#!/bin/bash

# Define variables
APP_NAME="OpenAVRStudio"
INSTALL_DIR="$HOME/.local/share/$APP_NAME"
DESKTOP_FILE="$HOME/.local/share/applications/$APP_NAME.desktop"
JAVA_SOURCE="OpenAVRStudio.java"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔧 Starting OpenAVR Studio Installer...${NC}"

# --- STEP 1: DETECT OS & INSTALL DEPENDENCIES ---
echo -e "${BLUE}📦 Checking and installing dependencies...${NC}"

if command -v apt-get &> /dev/null; then
    echo -e "   → Detected ${GREEN}Debian/Ubuntu/Mint${NC}"
    sudo apt-get update
    sudo apt-get install -y default-jdk gcc-avr avr-libc avrdude curl

elif command -v dnf &> /dev/null; then
    echo -e "   → Detected ${GREEN}Fedora/RHEL${NC}"
    sudo dnf install -y java-latest-openjdk-devel avr-gcc avr-libc avrdude curl

elif command -v pacman &> /dev/null; then
    echo -e "   → Detected ${GREEN}Arch Linux/Manjaro${NC}"
    sudo pacman -S --noconfirm jdk-openjdk avr-gcc avr-libc avrdude curl

else
    echo -e "${RED}❌ Error: Unsupported Package Manager.${NC}"
    echo "Please manually install: Java JDK, avr-gcc, avr-libc, and avrdude."
    read -p "Press Enter to continue if you have installed them manually..."
fi

# --- STEP 2: SETUP DIRECTORIES ---
echo -e "${BLUE}📂 Setting up directories...${NC}"
mkdir -p "$INSTALL_DIR"

# --- STEP 3: COMPILE ---
if [ ! -f "$JAVA_SOURCE" ]; then
    echo -e "${RED}❌ Error: $JAVA_SOURCE not found in current directory!${NC}"
    exit 1
fi

echo -e "${BLUE}☕ Compiling Application...${NC}"
cp "$JAVA_SOURCE" "$INSTALL_DIR/"
javac -d "$INSTALL_DIR" "$INSTALL_DIR/$JAVA_SOURCE"

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Compilation failed.${NC}"
    exit 1
fi

# --- STEP 4: CREATE RUN SCRIPT ---
echo "#!/bin/bash
cd \"$INSTALL_DIR\"
java $APP_NAME
" > "$INSTALL_DIR/run.sh"

chmod +x "$INSTALL_DIR/run.sh"

# --- STEP 5: FETCH ICON ---
echo -e "${BLUE}🖼️  Fetching Icon...${NC}"
curl -s -o "$INSTALL_DIR/icon.png" "https://cdn-icons-png.flaticon.com/512/2620/2620968.png"

# --- STEP 6: DESKTOP SHORTCUT ---
echo -e "${BLUE}📝 Creating Desktop Entry...${NC}"
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

# Refresh Gnome/DE
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null

echo -e "${GREEN}✅ Success! OpenAVR Studio is installed.${NC}"
echo "You can now find it in your App Menu."
