# OpenAVR Studio 

**OpenAVR Studio** is a lightweight, full-featured Integrated Development Environment (IDE) designed to bring the **Microchip (Atmel) Studio** experience to Linux. It provides a modern GUI for writing, compiling, simulating, and flashing AVR Assembly code without needing Windows or Wine.

It acts as a powerful frontend for the standard GNU AVR toolchain (`avr-gcc`, `avrdude`), adding features like syntax highlighting, real-time register visualization, and an integrated I/O simulator.

---

## 🚀 Key Features

### 🖥️ Editor & IDE

* **Syntax Highlighting:** VS Code-style colorization for Opcodes (Blue), Registers (Orange), Comments (Green), and Directives (Pink).
* **Auto-Patcher:** Automatically converts legacy Atmel syntax (e.g., `.INCLUDE "M32DEF.INC"`, `JMP`) into modern GCC-compatible syntax (`#include <avr/io.h>`, `RJMP`) on the fly.
* **Line Numbers:** Dynamic gutter with line tracking.

### 🐞 Simulator & Debugger

* **Visual I/O Inspector:** Watch `PORTA`, `PORTB`, etc., toggle in real-time as your code executes (perfect for testing LED logic without hardware).
* **Register View:** Live table view of General Purpose Registers (`R0` - `R31`) in Hex and Decimal.
* **Memory Map:** Inspect SRAM and I/O memory addresses.
* **Stepping Control:** `Run`, `Pause`, and `Step` through your assembly instructions one by one.

### ⚙️ Build & Flash

* **One-Click Build:** Assembles code using `avr-gcc` and generates `.hex`, `.elf`, and `.map` files.
* **Integrated Flashing:** dedicated GUI tab for `avrdude` to detect chips and flash firmware.
* **Device Selection:** Toolbar support for ATmega32, ATmega328P, and ATtiny85.

---

## 🛠️ Prerequisites

Since this is a frontend for the Linux AVR tools, you must have the toolchain installed.

**On Fedora Linux:**

```bash
sudo dnf install java-latest-openjdk-devel avr-gcc avr-libc avrdude

```

**Permissions (Important for Flashing Hardware):**
Add your user to the dialout group to access USB programmers without `sudo`:

```bash
sudo usermod -a -G dialout $USER
# Log out and log back in for this to take effect.

```

---

## 📦 Installation & Running

OpenAVR Studio is a single-file portable Java application.

1. **Download** the `OpenAVRStudio.java` file to your folder.
2. **Compile** the application:
```bash
javac OpenAVRStudio.java

```


3. **Run**:
```bash
java OpenAVRStudio

```



---

## 📖 Usage Guide

### 1. Writing Code

The editor supports standard AVR Assembly. You can use legacy Atmel syntax; the IDE will handle the conversion for GCC.

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

1. Click **▶ Run** in the toolbar.
2. The code will compile and the simulator will start.
3. Look at the **I/O Visualizer** (Top Right) under `PORTB`. You will see the checkbox for **Bit 0** toggling on and off.
4. Click **⏸ Pause**, then **⤵ Step** to execute line-by-line.

### 3. Flashing to Chip

1. Connect your Programmer (USBasp, Arduino as ISP).
2. Go to the **Device Programming** tab.
3. Click **Write Flash**.

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

## 🤝 Contributing

This project is open-source. Feel free to fork and add support for:

* More AVR instructions (current simulator supports core logic + I/O).
* Interrupt handling in the simulator.
* Custom themes.

**License:** MIT License
