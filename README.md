# OpenAVR Studio

**OpenAVR Studio** is a lightweight, full-featured Integrated Development Environment (IDE) designed to bring the **Microchip (Atmel) Studio** experience to Linux. It provides a modern GUI for writing, compiling, simulating, and flashing AVR Assembly code without needing Windows or Wine.

It acts as a powerful frontend for the standard GNU AVR toolchain (`avr-gcc`, `avrdude`), adding features like syntax highlighting, real-time register visualization, and an integrated I/O simulator.

---

## 🚀 Key Features

### 🖥️ Editor & IDE

* **Syntax Highlighting:** VS Code-style colorization for Opcodes (Blue), Registers (Orange), Comments (Green), and Directives (Pink).
* **Auto-Patcher:** Automatically converts legacy Atmel syntax (e.g., `.INCLUDE "M32DEF.INC"`, `JMP`) into modern GCC-compatible syntax (`#include <avr/io.h>`, `RJMP`) on the fly.
* **Line Numbers:** Dynamic gutter with synchronized line tracking.
* **Standard Controls:** Full support for Undo/Redo (`Ctrl+Z`, `Ctrl+Y`), Copy/Paste, and File operations.

### 🐞 Simulator & Debugger

* **Visual I/O Inspector:** Watch `PORTA`, `PORTB`, etc., toggle in real-time as your code executes. Perfect for testing LED logic without hardware.
* **Extended Register View:** Live table showing General Purpose Registers (`R0` - `R31`) in **Binary**, **Hex (0x00)**, and **Decimal**.
* **Memory Map:** Inspect SRAM and I/O memory addresses.
* **One-Click Execution:** Simple `Run & Execute` button handles building, linking, and simulation start/stop automatically.

### ⚙️ Build & Flash

* **One-Click Build:** Assembles code using `avr-gcc` and generates `.hex`, `.elf`, and `.map` files.
* **Integrated Flashing:** dedicated GUI for `avrdude` to detect chips and flash firmware.
* **Device Selection:** Toolbar support for ATmega32 and ATmega328P with clock frequency selection.

---

## 📦 One-Click Installation (Universal)

You do **not** need to manually install dependencies. The installer script detects your OS (Fedora, Debian/Ubuntu, Arch) and handles everything.

### Install

1. Download `OpenAVRStudio.java` and `install.sh` to a folder.
2. Run the installer:
```bash
chmod +x install.sh
./install.sh

```


*This will compile the app, install dependencies (if missing), and create a Desktop Shortcut.*

### Uninstall

To remove the app and shortcut:

```bash
chmod +x uninstall.sh
./uninstall.sh

```

---

## 📖 Usage Guide

### 1. Writing Code

The editor supports standard AVR Assembly. You can use legacy Atmel syntax; the IDE will handle the conversion for GCC automatically.

**Example Blink Code:**

```asm
.INCLUDE "M32DEF.INC"

LDI R16, 0xFF
OUT DDRB, R16    ; Set Port B as Output

LOOP:
  SBI PORTB, 0   ; Turn on Bit 0
  RCALL DELAY
  CBI PORTB, 0   ; Turn off Bit 0
  RCALL DELAY
  RJMP LOOP

```

### 2. Simulation (No Hardware)

1. Click **▶ RUN & EXECUTE** in the toolbar.
2. The code will compile, and the simulator will start immediately.
3. Look at the **I/O Visualizer** (Top Right) under `PORTB`. You will see the checkbox for **Bit 0** toggling on and off.
4. Click the button again (now **⏹ STOP**) to end the simulation.

### 3. Flashing to Chip

1. Connect your Programmer (USBasp, Arduino as ISP).
2. Go to the **Device Programming** tab (if available in your build) or use the build output hex file.

---

## ⌨️ Keyboard Shortcuts

| Shortcut | Action |
| --- | --- |
| `Ctrl` + `S` | **Quick Save** (Overwrites current file) |
| `Ctrl` + `Shift` + `S` | **Save As...** |
| `Ctrl` + `O` | **Open File** |
| `Ctrl` + `Z` | **Undo** |
| `Ctrl` + `Y` | **Redo** |

---

## 🛠️ Manual Build (Optional)

If you prefer not to use the installer script:

1. **Install Dependencies:**
* **Fedora:** `sudo dnf install java-latest-openjdk-devel avr-gcc avr-libc avrdude`
* **Debian/Ubuntu:** `sudo apt install default-jdk gcc-avr avr-libc avrdude`
* **Arch:** `sudo pacman -S jdk-openjdk avr-gcc avr-libc avrdude`


2. **Compile & Run:**
```bash
javac OpenAVRStudio.java
java OpenAVRStudio

```



**Permissions Note:**
To access USB programmers without `sudo`, add your user to the dialout group:

```bash
sudo usermod -a -G dialout $USER
# Log out and log back in required.

```

---

## 🤝 Contributing

This project is open-source. Feel free to fork and add support for:

* More AVR instructions (current simulator supports core logic + I/O).
* Interrupt handling in the simulator.
* Custom themes.

**Developer:** [isg32](https://github.com/isg32)

**License:** MIT License
