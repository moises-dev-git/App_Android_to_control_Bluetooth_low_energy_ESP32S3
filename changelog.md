# Change Log & Version History

This document records the full history of modifications made to the **ESP32-S3 BLE HID Controller** firmware and **Android App**, detailing resolved issues, required configurations, and backup procedures.

---

## 📝 Summary of Completed Modifications

### 1. ESP32-S3 Firmware ([`ESP32-S3.ino`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/ESP32-S3.ino))

#### Key Changes:
* **USB Product Name Changed to `"Mouse USB HID"`:**
  - Configured `USB.productName("Mouse USB HID");` and `USB.manufacturerName("Dispositivo HID");` for proper display in Windows Device Manager and Devices & Printers.
* **Case-Insensitive Shortcut Parsing (`CTRL+Z` vs `CTRL+z`):**
  - **Issue:** Arduino's native `USBHIDKeyboard` library automatically presses **SHIFT** when passed uppercase characters (e.g. `'Z'`), causing `CTRL+Z` to be sent as `CTRL + SHIFT + Z` (Redo instead of Undo).
  - **Fix:** `processKeyCombination()` automatically converts `'A'-'Z'` to `'a'-'z'` scancodes before calling `keyboard.press()`.
* **Full Special Keys Support (`parseSpecialKey`):**
  - Mapped `ENTER`, `ESC`, `TAB`, `DEL`, `BACKSPACE`, `INSERT`, `HOME`, `END`, `PAGEUP`, `PAGEDOWN`, `UP`, `DOWN`, `LEFT`, `RIGHT`, `SPACE`, `CAPSLOCK`, and `F1` through `F12`.
* **Direct and Humanized Mouse Pointer Movement (`MOVE:x,y` and `MOVE_HUMAN:x,y,speed`):**
  - Updated `moveMouseTo(targetX, targetY, speed, isHuman)` helper function.
  - **Direct Mode (`MOVE:x,y`):** Fast straight movement after quick top-left reset.
  - **Humanized Mode (`MOVE_HUMAN:x,y,speed` or `MOVE:x,y,speed,1`):** Smooth reset to (0,0) with deceleration, followed by a **Quadratic Bézier Curve** to `(x, y)` with natural **Ease-In-Out** acceleration/deceleration. Configurable speed parameter (1 = slow/smooth to 10 = fast flick).
* **BLE Discovery Resolution on Android:**
  - Migrated to Espressif's official `BLEDevice.h` (Bluedroid) stack.
  - Added mandatory `BLE2902` (CCCD) descriptor (`pTxCharacteristic->addDescriptor(new BLE2902())`).
  - Added connected count check in `loop()` (`pServer->getConnectedCount() > 0`) to prevent crash loops.

---

### 2. Android Application

* **Categorized Horizontal Carousel in `MacroActivity`:**
  - Implemented category-based horizontal scroll views for all special keys (Shortcuts, Editing & Control, Navigation & Arrows, Function Keys F1-F12, and Mouse Actions including `Move Direto` and `Move Humano`).
* **512-Byte MTU Expansion ([`ControlActivity.java`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/ControlActivity.java)):**
  - Requests `requestMtu(512)` upon connection, preventing Android's default 20-byte payload truncation on long macros.
* **Low-Latency BLE Scanning & GPS Check ([`MainActivity.java`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/MainActivity.java)):**
  - Configured `BluetoothLeScanner` with `SCAN_MODE_LOW_LATENCY` and added automatic GPS location check.

---

### 3. Test-Driven Development (TDD) & Fragmented Macro Transmission

* **Comprehensive TDD Test Suite (Android & C++):**
  - Added unit test suites for data models, JSON serialization, and business rules ([`MacroTest.kt`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/test/java/com/example/esp32app/model/MacroTest.kt), [`MacroSerializerTest.kt`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/test/java/com/example/esp32app/data/MacroSerializerTest.kt), [`MacroBusinessLogicTest.kt`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/test/java/com/example/esp32app/data/MacroBusinessLogicTest.kt)).
  - Added BLE protocol formatter and parser tests ([`BleProtocolTest.kt`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/test/java/com/example/esp32app/protocol/BleProtocolTest.kt)).
  - Added native C++ firmware protocol validation test suite ([`test_esp32_protocol.cpp`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/tests_firmware/test_esp32_protocol.cpp)).
  - Added unified regression runner script [`run_tests.sh`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/run_tests.sh).
* **Fragmented Macro Transmission (Unlimited Macro Size Support):**
  - Extracted command formatting and packet chunking into [`BleProtocol.kt`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/protocol/BleProtocol.kt) (`splitMacroIntoSteps`).
  - Automatically slices long text blocks into safe <= 180-byte chunks, eliminating single-packet MTU limitations (~505 bytes).
  - Implemented asynchronous coroutine sequence execution with GATT write confirmation (`onCharacteristicWrite`) in [`ControlActivity.kt`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/ControlActivity.kt).
  - Real-time step progress indicator and immediate cancellation button in the UI.
* **Firmware MTU & Buffer Overflow Protection ([`ESP32-S3.ino`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/ESP32-S3.ino)):**
  - Set `BLEDevice::setMTU(517)`.
  - Added safe truncation for response notification ACKs to prevent BLE buffer overflow.

---

## ⚙️ Arduino IDE Compilation Settings

When flashing ESP32-S3 firmware:
* **Board:** `ESP32S3 Dev Module`
* **Partition Scheme:** `Huge APP (3MB No OTA / 1MB SPIFFS)`
* **USB CDC On Boot:** `Enabled`
* **USB Mode:** `Hardware CDC and JTAG`
