#!/bin/bash

# Define variables
APP_NAME="OpenAVRStudio"
INSTALL_DIR="$HOME/.local/share/$APP_NAME"
DESKTOP_FILE="$HOME/.local/share/applications/$APP_NAME.desktop"
JAVA_SOURCE="OpenAVRStudio.java"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔧 Starting OpenAVR Studio Installer...${NC}"

# --- STEP 1: CHECK EXISTING DEPENDENCIES ---
echo -e "${BLUE}🔍 Checking for required tools...${NC}"

MISSING_DEPS=false
REQUIRED_TOOLS=("javac" "avr-gcc" "avrdude" "curl")

for tool in "${REQUIRED_TOOLS[@]}"; do
    if ! command -v "$tool" &> /dev/null; then
        echo -e "   [${RED}MISSING${NC}] $tool"
        MISSING_DEPS=true
    else
        echo -e "   [${GREEN}FOUND${NC}]   $tool"
    fi
done

# --- STEP 2: INSTALL IF MISSING ---
if [ "$MISSING_DEPS" = true ]; then
    echo -e "${YELLOW}⚠️  Some dependencies are missing. Installing them now...${NC}"
    
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
        echo -e "${RED}❌ Error: Could not detect package manager.${NC}"
        echo "Please manually install: Java JDK, avr-gcc, avr-libc, and avrdude."
        exit 1
    fi
else
    echo -e "${GREEN}✅ All dependencies are already installed. Skipping system updates.${NC}"
fi

# --- STEP 3: SETUP DIRECTORIES ---
echo -e "${BLUE}📂 Setting up directories...${NC}"
mkdir -p "$INSTALL_DIR"

# --- STEP 4: COMPILE ---
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

# --- STEP 5: CREATE RUN SCRIPT ---
echo "#!/bin/bash
cd \"$INSTALL_DIR\"
java $APP_NAME
" > "$INSTALL_DIR/run.sh"

chmod +x "$INSTALL_DIR/run.sh"

# --- STEP 6: FETCH ICON ---
echo -e "${BLUE}🖼️  Fetching Icon...${NC}"
curl -s -o "$INSTALL_DIR/icon.png" "https://cdn-icons-png.flaticon.com/512/2620/2620968.png"

# --- STEP 7: DESKTOP SHORTCUT ---
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

echo -e "${GREEN}🎉 Success! OpenAVR Studio is ready.${NC}"
