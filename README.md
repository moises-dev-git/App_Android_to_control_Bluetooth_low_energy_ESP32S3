# ESP32-S3 BLE HID Controller & Android App

Language: [English](README.md) | [Português](README_PT.md)

This repository contains a complete end-to-end solution for remotely controlling a computer using an **ESP32-S3** configured as a native USB Human Interface Device (HID - Keyboard and Mouse) via Bluetooth Low Energy (BLE), paired with a native Android application.

---

## 🚀 Key Features

- **Native USB Keyboard & Mouse Emulation:** The ESP32-S3 acts as a physical USB keyboard and mouse (`Mouse USB HID`).
- **Dynamic Key Combinations:** Supports any key combo (`CTRL+SHIFT+ESC`, `ALT+F4`, `WIN+R`, `CTRL+ALT+DEL`, `WIN+D`, etc.), case-insensitive.
- **Special Keys Mapping:** Full support for `ENTER`, `ESC`, `TAB`, `DEL`/`DELETE`, `BACKSPACE`, `INSERT`, `HOME`, `END`, `PAGEUP`, `PAGEDOWN`, `CAPSLOCK`, directional arrows, and function keys `F1` through `F12`.
- **Mouse Movement & Clicking:** Mouse pointer positioning with **Direct** (`MOVE:x,y`) or **Humanized** (`MOVE_HUMAN:x,y,speed`) mode using Quadratic Bézier curves and natural Ease-In-Out acceleration/deceleration, plus adjustable speed (1-10) and clicks.
- **Sequential Macro Execution (`SEQ:`):** Automated routines with human/direct pointer positioning, delays (`DELAY:ms`), text typing, and key shortcuts.
- **Categorized Key Carousel in Android:** Category-based horizontal carousel interface in the Android app for easy key selection.

---

## 📚 Libraries Used in ESP32-S3

The firmware relies **exclusively on native libraries included in Espressif's official ESP32 Arduino Core** (no 3rd-party dependencies required):

```cpp
#include "USB.h"             // ESP32-S3 native USB hardware stack
#include "USBHIDKeyboard.h"   // Native USB HID Keyboard emulation
#include "USBHIDMouse.h"      // Native USB HID Mouse emulation

#include <BLEDevice.h>        // Official Espressif Bluedroid BLE stack
#include <BLEServer.h>        // BLE GATT Server
#include <BLEUtils.h>         // Data conversions & UUID utilities
#include <BLE2902.h>          // Mandatory 0x2902 (CCCD) Descriptor for Android
```

> **Why is the `BLE2902` descriptor mandatory?**  
> Android OS drops and ignores BLE characteristic notifications (`PROPERTY_NOTIFY`) if the Client Characteristic Configuration Descriptor (`0x2902` / CCCD) is not registered. Including `BLE2902` guarantees full Android BLE GATT scanner compatibility.

---

## ⚙️ Mandatory Arduino IDE Settings

In the **Tools** menu of Arduino IDE, select the following exact settings before flashing the firmware:

| Setting | Option to Select | Technical Rationale |
| :--- | :--- | :--- |
| **Board** | `ESP32S3 Dev Module` | Official development board definition for ESP32-S3. |
| **Partition Scheme** | **`Huge APP (3MB No OTA / 1MB SPIFFS)`** | **REQUIRED:** The Bluedroid BLE + USB HID stack exceeds 1.3 MB. Default partition causes flash overflow. *Huge APP* grants 3 MB for application code. |
| **USB CDC On Boot** | `Enabled` | Allows viewing `Serial.println()` output in Serial Monitor at 115200 baud. |
| **USB Mode** | `Hardware CDC and JTAG` *(or USB-OTG TinyUSB)* | Enables native USB Keyboard and Mouse peripheral emulation. |
| **ESP32 Core** | `esp32` by Espressif Systems (v2.0.x or v3.x) | Official Espressif board manager package. |

---

## 📱 Android Device Requirements

* **Location / GPS ENABLED:** Android OS requires Location/GPS to be **ON** in quick settings for BLE peripheral scanning.
* **Bluetooth / Nearby Devices Permissions:** Grant all requested permissions on app first launch.

---

## 📂 Repository Structure

* [`ESP32-S3.ino`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/ESP32-S3.ino): ESP32-S3 firmware source code.
* [`App Android/`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android): Native Java Android Studio project.
* [`project_documentation.md`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/project_documentation.md): Comprehensive technical architecture, BLE protocol spec, and troubleshooting guide.
* [`changelog.md`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/changelog.md): Complete history of fixes and modifications.
