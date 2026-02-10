# OpenAVR Studio

**OpenAVR Studio** is a lightweight, full-featured Integrated Development Environment (IDE) designed to bring the **Microchip (Atmel) Studio** experience to **Linux and Windows**. It provides a modern GUI for writing, compiling, simulating, and flashing AVR Assembly code without needing heavy proprietary software.

It acts as a powerful frontend for the standard GNU AVR toolchain (`avr-gcc`, `avrdude`), adding features like syntax highlighting, real-time register visualization, and an integrated I/O simulator.

---

## 🚀 Key Features

### 🖥️ Editor & IDE
* **Cross-Platform:** Native support for Linux (`.sh`) and Windows (`.bat`).
* **Syntax Highlighting:** VS Code-style colorization for Opcodes, Registers, Comments, and Directives.
* **Auto-Patcher:** Automatically converts legacy Atmel syntax (e.g., `.INCLUDE "M32DEF.INC"`) into modern GCC-compatible syntax on the fly.
* **Standard Controls:** Full support for Undo/Redo (`Ctrl+Z`, `Ctrl+Y`), Copy/Paste, and File operations.

### 🐞 Simulator & Debugger
* **Visual I/O Inspector:** Watch `PORTA`, `PORTB`, etc., toggle in real-time as your code executes. Perfect for testing LED logic without hardware.
* **Extended Register View:** Live table showing General Purpose Registers (`R0` - `R31`) in **Binary**, **Hex**, and **Decimal**.
* **One-Click Execution:** Simple `Run & Execute` button handles building, linking, and simulation start/stop automatically.

### ⚙️ Build & Flash
* **Automated Toolchain Setup:** On Windows, the installer automatically downloads and configures the AVR-GCC compiler for you.
* **One-Click Build:** Assembles code using `avr-gcc` and generates `.hex` and `.elf` files.
* **Device Selection:** Toolbar support for ATmega32 and ATmega328P.

---

## 📦 Installation

### 🪟 Windows (Recommended)
You do **not** need to manually install the compiler. The installer handles the AVR toolchain download.

1. Download `OpenAVRStudio.java` and `install.bat` to a folder.
2. **Right-click `install.bat`** and select **"Run as Administrator"**.
3. The script will:
   * Download the AVR Toolchain (AVR-GCC).
   * Compile the Java application.
   * Create a **Desktop Shortcut**.
4. *Note: You may need to restart your computer after the first install for the "Path" variables to update.*

### 🐧 Linux (Universal)
The installer detects your package manager (apt, dnf, or pacman) and installs dependencies automatically.

1. Download `OpenAVRStudio.java` and `install.sh`.
2. Run the installer:
```bash
chmod +x install.sh
./install.sh
```

---

## 📖 Usage Guide

### 1. Writing Code
The editor supports standard AVR Assembly. You can use legacy Atmel syntax; the IDE handles the conversion for GCC automatically.

**Example Blink Code:**
```asm
LDI R16, 0xFF
OUT DDRB, R16    ; Set Port B as Output

LOOP:
  SBI PORTB, 0   ; Turn on Bit 0 (LED ON)
  CBI PORTB, 0   ; Turn off Bit 0 (LED OFF)
  RJMP LOOP
```

### 2. Simulation (No Hardware)
1. Click **▶ RUN & EXECUTE** in the toolbar.
2. The code will compile, and the simulator starts.
3. Look at the **I/O Visualizer** (Top Right) under `PORTB`. You will see the checkbox for **Bit 0** toggling.
4. Click **⏹ STOP** to end the simulation.

---

## ⌨️ Keyboard Shortcuts

| Shortcut | Action |
| --- | --- |
| `Ctrl` + `S` | **Quick Save** |
| `Ctrl` + `O` | **Open File** |
| `Ctrl` + `Z` | **Undo** |
| `Ctrl` + `Y` | **Redo** |

---

## 🛠️ Manual Build (Optional)

### Linux
1. Install `default-jdk`, `gcc-avr`, and `avr-libc`.
2. Run:
   ```bash
   javac OpenAVRStudio.java
   java OpenAVRStudio
   ```

### Windows
1. Install [Java JDK](https://www.oracle.com/java/technologies/downloads/).
2. Install the [AVR Toolchain](https://www.microchip.com/en-us/tools-resources/develop/microchip-studio/gcc-compilers) and add the `bin` folder to your System PATH.
3. Run:
   ```cmd
   javac OpenAVRStudio.java
   java OpenAVRStudio
   ```

---

## 🤝 Contributing

This project is open-source. Feel free to fork and add support for:
* Interrupt handling in the simulator.
* Support for additional AVR chips (ATtiny, etc.).
* Improved memory inspection views.

**Developer:** [isg32](https://github.com/isg32)  
**License:** MIT License
