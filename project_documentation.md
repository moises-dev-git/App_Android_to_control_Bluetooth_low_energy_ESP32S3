# Full Project Documentation: BLE HID Controller with ESP32-S3

This document details the full system architecture, libraries, Android requirements, exact Arduino IDE configuration, and complete troubleshooting history to ensure the **ESP32-S3** operates flawlessly via **Bluetooth Low Energy (BLE)** and **USB HID**.

---

## 1. System Architecture Overview

The system bridges an Android smartphone and a host computer through the ESP32-S3:
* **ESP32-S3 (BLE Server + USB HID Peripheral):**
  * Connected to the host computer's USB port, identifying as **`Mouse USB HID`**.
  * Broadcasts BLE signals advertising Nordic UART Service (`6E400001-B5A3-F393-E0A9-E50E24DCCA9E`).
  * Receives text commands via BLE from the phone and injects them instantly into the computer's USB host.
* **Android Application (Central BLE Client):**
  * Performs low-latency scanning for nearby BLE peripherals.
  * Requests MTU expansion to 512 bytes upon connection, allowing long macros to be sent in a single packet.
  * Provides a **Categorized Horizontal Carousel UI** in the Macro Manager to insert special keys (`F1-F12`, `Enter`, `Esc`, `Tab`, `Space`, `Backspace`, `Delete`, `Insert`, `Home`, `End`, `PageUp`, `PageDown`, `Arrows`, `Caps Lock`, shortcuts, and mouse actions).

---

## 2. USB Device Identification (Windows vs Linux)

### 💻 Differences between Linux `lsusb` and Windows Device Manager:
1. **On Linux (`lsusb`):**
   * `lsusb` checks hardware Vendor ID (`0x303A`) and Product ID (`0x1001`) against `/usr/share/hwdata/usb.ids`. Therefore it lists the string registered in Linux's global database: `Espressif Systems ESP32S3_DEV`.
   * Running `lsusb -v` displays the actual product description string transmitted by the chip: `iProduct: Mouse USB HID`.
2. **On Windows (Device Manager and Devices & Printers):**
   * Windows **reads the product description string transmitted by ESP32-S3 firmware** (`USB.productName("Mouse USB HID")`).
   * On Windows, the device appears under *Human Interface Devices (HID)*, *Keyboards*, and *Mice* as **`Mouse USB HID`**.

---

## 3. Mandatory Arduino IDE Settings

To compile and flash the firmware to **ESP32-S3** without memory overflow or Bluetooth stack crashes:

### ⚙️ Tools Menu Options in Arduino IDE:

| IDE Setting | Selected Option | Technical Rationale |
| :--- | :--- | :--- |
| **Board** | `ESP32S3 Dev Module` | Development board definition for ESP32-S3. |
| **Partition Scheme** | **`Huge APP (3MB No OTA / 1MB SPIFFS)`** | **REQUIRED:** Bluedroid BLE + USB HID stack exceeds 1.3 MB. Default 4MB partition causes flash overflow. *Huge APP* grants 3 MB for application code. |
| **USB CDC On Boot** | `Enabled` | Enables `Serial.println()` output in Serial Monitor at 115200 baud. |
| **USB Mode** | `Hardware CDC and JTAG` *(or USB-OTG TinyUSB)* | Enables native USB Keyboard and Mouse peripheral emulation. |
| **ESP32 Core** | `esp32` by Espressif Systems (v2.0.x or v3.x) | Official Espressif board manager package. |

---

## 4. Firmware Libraries (`ESP32-S3.ino`)

All libraries used are **native to Espressif's official Arduino package**:

* `<USB.h>`: ESP32-S3 native USB hardware stack controller.
* `<USBHIDKeyboard.h>`: Native USB HID Keyboard emulation.
* `<USBHIDMouse.h>`: Native USB HID Mouse emulation.
* `<BLEDevice.h>`: Official **Bluedroid** BLE stack by Espressif.
* `<BLEServer.h>`: BLE GATT Server creation.
* `<BLEUtils.h>`: Data conversions and UUID utilities.
* `<BLE2902.h>`: **Mandatory `0x2902` (CCCD) Descriptor** for Android notifications.

---

## 5. Android App Interface & Categorized Carousel

The Macro Manager screen features **Horizontal ScrollViews** organized into 5 clean, uncluttered categories:

* ⚡ **Quick Shortcuts:** `Ctrl+Z`, `Ctrl+C`, `Ctrl+V`, `Ctrl+Shift+Esc`, `Alt+F4`, `Win+D`.
* 🎹 **Editing & Control:** `Enter`, `Tab`, `Esc`, `Space`, `Backspace`, `Delete`, `Insert`, `Home`, `End`, `PageUp`, `PageDown`.
* 🎯 **Navigation & Arrows:** `↑ Up`, `↓ Down`, `← Left`, `→ Right`, `Caps Lock`.
* 🛠️ **Function Keys (F1 to F12):** `F1`, `F2`, `F3`, `F4`, `F5`, `F6`, `F7`, `F8`, `F9`, `F10`, `F11`, `F12`.
* 🖱️ **Actions & Commands:** `TEXT:`, `DELAY:500`, `Left Click`, `Right Click`.

---

## 6. Command Protocol & BLE Spec

| Command Format | Example | Action Executed on Host Computer |
| :--- | :--- | :--- |
| `TXT:text` | `TXT:Hello World` | Types text literally. |
| `CMD:COPY` | `CMD:COPY` | Presses `Ctrl + c`. |
| `CMD:PASTE` | `CMD:PASTE` | Presses `Ctrl + v`. |
| `CMD:CHROME` | `CMD:CHROME` | Presses `Win + r`, types "chrome", and presses `Enter`. |
| `CMD:MOUSE_LCLICK` | `CMD:MOUSE_LCLICK` | Mouse left click. |
| `CMD:MOUSE_RCLICK` | `CMD:MOUSE_RCLICK` | Mouse right click. |
| `CMD:MOUSE:dx,dy` | `CMD:MOUSE:10,-20` | Moves mouse cursor relatively (x, y). |
| `CMD:KEY:combo` | `CMD:KEY:CTRL+SHIFT+ESC` | Presses dynamic key combination. |
| `SEQ:lines` | `SEQ:ALT+F4\nDELAY:500\nENTER` | Executes multi-line macro sequence. |
