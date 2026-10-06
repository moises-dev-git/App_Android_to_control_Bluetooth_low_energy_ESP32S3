#include "USB.h"
#include "USBHIDKeyboard.h"
#include "USBHIDMouse.h"

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

USBHIDKeyboard keyboard;
USBHIDMouse Mouse;

BLEServer *pServer = nullptr;
BLECharacteristic *pTxCharacteristic = nullptr; // Tx Characteristic for sending data

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
      Serial.println("Device Connected!");
    }

    void onDisconnect(BLEServer* pServer) override {
      Serial.println("Device Disconnected! Restarting advertising...");
      delay(300);
      BLEDevice::startAdvertising();
    }
};

uint8_t parseSpecialKey(String key) {
  key.toUpperCase();
  key.trim();
  if (key == "ENTER" || key == "RETURN") return KEY_RETURN;
  if (key == "ESC" || key == "ESCAPE") return KEY_ESC;
  if (key == "TAB") return KEY_TAB;
  if (key == "BACKSPACE" || key == "BS") return KEY_BACKSPACE;
  if (key == "DELETE" || key == "DEL") return KEY_DELETE;
  if (key == "INSERT" || key == "INS") return KEY_INSERT;
  if (key == "HOME") return KEY_HOME;
  if (key == "END") return KEY_END;
  if (key == "PAGEUP" || key == "PAGE_UP") return KEY_PAGE_UP;
  if (key == "PAGEDOWN" || key == "PAGE_DOWN") return KEY_PAGE_DOWN;
  if (key == "UP" || key == "UP_ARROW") return KEY_UP_ARROW;
  if (key == "DOWN" || key == "DOWN_ARROW") return KEY_DOWN_ARROW;
  if (key == "LEFT" || key == "LEFT_ARROW") return KEY_LEFT_ARROW;
  if (key == "RIGHT" || key == "RIGHT_ARROW") return KEY_RIGHT_ARROW;
  if (key == "SPACE") return ' ';
  if (key == "CAPSLOCK" || key == "CAPS_LOCK") return KEY_CAPS_LOCK;
  if (key == "F1") return KEY_F1;
  if (key == "F2") return KEY_F2;
  if (key == "F3") return KEY_F3;
  if (key == "F4") return KEY_F4;
  if (key == "F5") return KEY_F5;
  if (key == "F6") return KEY_F6;
  if (key == "F7") return KEY_F7;
  if (key == "F8") return KEY_F8;
  if (key == "F9") return KEY_F9;
  if (key == "F10") return KEY_F10;
  if (key == "F11") return KEY_F11;
  if (key == "F12") return KEY_F12;
  return 0;
}

void processKeyCombination(String comboStr) {
  comboStr.trim();
  if (comboStr.length() == 0) return;

  // Replace underscores with '+' for legacy formats (e.g. CTRL_F4 -> CTRL+F4)
  if (comboStr.indexOf('+') == -1 && comboStr.indexOf('_') != -1) {
    comboStr.replace('_', '+');
  }

  // Ensure previous keys are released before starting a new combination
  keyboard.releaseAll();
  delay(10);

  int startIndex = 0;
  bool pressedAny = false;

  while (startIndex < comboStr.length()) {
    int nextPlus = comboStr.indexOf('+', startIndex);
    if (nextPlus == -1) nextPlus = comboStr.length();

    String part = comboStr.substring(startIndex, nextPlus);
    part.trim();
    String partUpper = part;
    partUpper.toUpperCase();

    if (partUpper == "CTRL" || partUpper == "CONTROL") {
      keyboard.press(KEY_LEFT_CTRL);
      pressedAny = true;
    } else if (partUpper == "ALT") {
      keyboard.press(KEY_LEFT_ALT);
      pressedAny = true;
    } else if (partUpper == "SHIFT") {
      keyboard.press(KEY_LEFT_SHIFT);
      pressedAny = true;
    } else if (partUpper == "WIN" || partUpper == "GUI" || partUpper == "SUPER" || partUpper == "META") {
      keyboard.press(KEY_LEFT_GUI);
      pressedAny = true;
    } else {
      uint8_t specialKey = parseSpecialKey(partUpper);
      if (specialKey != 0) {
        keyboard.press(specialKey);
        pressedAny = true;
      } else if (part.length() == 1 || partUpper.length() == 1) {
        char c = (part.length() == 1) ? part[0] : partUpper[0];
        // Convert uppercase (A-Z) to lowercase (a-z) when simulating physical scancodes.
        // This prevents USBHID library from automatically injecting SHIFT for capital letters.
        if (c >= 'A' && c <= 'Z') {
          c = c + ('a' - 'A');
        }
        keyboard.press(c);
        pressedAny = true;
      }
    }

    startIndex = nextPlus + 1;
    delay(10);
  }

  if (pressedAny) {
    delay(120); // Essential key hold delay for host OS (Windows/Linux) to register USB HID shortcut
  }
  keyboard.releaseAll();
  delay(20);
}

void moveMouseTo(int targetX, int targetY, int speed = 5, bool isHuman = false) {
  if (targetX < 0) targetX = 0;
  if (targetY < 0) targetY = 0;

  // Limita velocidade entre 1 (lento) e 10 (rápido)
  if (speed < 1) speed = 1;
  if (speed > 10) speed = 10;

  if (!isHuman) {
    // --- MODO DIRETO / RÁPIDO ---
    // 1. Reseta para o canto superior esquerdo (0,0)
    for (int i = 0; i < 60; i++) {
      Mouse.move(-127, -127);
      delay(2);
    }

    // 2. Move em passos retos de 127
    while (targetX > 0 || targetY > 0) {
      int stepX = (targetX > 127) ? 127 : targetX;
      int stepY = (targetY > 127) ? 127 : targetY;
      Mouse.move(stepX, stepY);
      targetX -= stepX;
      targetY -= stepY;
      delay(2);
    }
    return;
  }

  // --- MODO HUMANIZADO (Curva de Bézier + Ease-In-Out) ---
  // speed 1 (lento): 120 passos, delay 8ms
  // speed 5 (normal): 60 passos, delay 4ms
  // speed 10 (rápido): 25 passos, delay 2ms
  int steps = map(speed, 1, 10, 120, 25);
  int stepDelay = map(speed, 1, 10, 8, 2);

  // 1. Zerar posição (0,0) com desaceleração suave
  for (int i = 0; i < steps; i++) {
    float u = (float)i / (float)steps;
    float ease = u * u * (3.0f - 2.0f * u);
    int dx = (int)(-127.0f * (1.0f - ease * 0.3f));
    int dy = (int)(-127.0f * (1.0f - ease * 0.3f));
    if (dx < -127) dx = -127;
    if (dy < -127) dy = -127;
    Mouse.move(dx, dy);
    delay(stepDelay);
  }
  // Garantia de colisão com canto superior esquerdo (0,0)
  for (int i = 0; i < 10; i++) {
    Mouse.move(-127, -127);
    delay(1);
  }

  if (targetX == 0 && targetY == 0) return;

  // 2. Mover de (0,0) para (targetX, targetY) usando Curva de Bézier Quadrática
  // P0 = (0, 0), P2 = (targetX, targetY)
  // P1 = Ponto de controle criando o arco de movimento humano natural
  float p1X = (float)targetX * 0.5f - (float)targetY * 0.15f;
  float p1Y = (float)targetY * 0.5f + (float)targetX * 0.15f;

  int lastX = 0;
  int lastY = 0;

  for (int i = 1; i <= steps; i++) {
    float u = (float)i / (float)steps;
    // Ease-In-Out (Smoothstep): aceleração suave no início e desaceleração na chegada
    float t = u * u * (3.0f - 2.0f * u);

    // Curva de Bézier: B(t) = (1-t)^2 * P0 + 2*(1-t)*t * P1 + t^2 * P2
    float invT = 1.0f - t;
    float currX = 2.0f * invT * t * p1X + t * t * (float)targetX;
    float currY = 2.0f * invT * t * p1Y + t * t * (float)targetY;

    int roundedX = (i == steps) ? targetX : (int)round(currX);
    int roundedY = (i == steps) ? targetY : (int)round(currY);

    int dx = roundedX - lastX;
    int dy = roundedY - lastY;

    while (dx != 0 || dy != 0) {
      int subDx = (dx > 127) ? 127 : ((dx < -127) ? -127 : dx);
      int subDy = (dy > 127) ? 127 : ((dy < -127) ? -127 : dy);

      Mouse.move(subDx, subDy);
      dx -= subDx;
      dy -= subDy;
      if (dx != 0 || dy != 0) delay(1);
    }

    lastX = roundedX;
    lastY = roundedY;

    delay(stepDelay);
  }
}

// Modo de Acentuação:
// 0 = Teclas Mortas / Dead Keys (Recomendado para Linux Mint & Windows com ABNT2 / US-Intl)
// 1 = Linux GTK Unicode (Ctrl+Shift+U + Hex + Enter -> Funciona no Linux Mint com QUALQUER teclado)
// 2 = Windows Alt Codes (Alt + 0XXX no teclado numérico)
int accentMode = 0;

void sendDeadKeyAccent(char accent, char letter) {
  keyboard.write(accent);
  delay(15);
  keyboard.write(letter);
  delay(15);
}

void sendLinuxUnicode(uint16_t unicode) {
  keyboard.press(KEY_LEFT_CTRL);
  keyboard.press(KEY_LEFT_SHIFT);
  keyboard.press('u');
  delay(20);
  keyboard.releaseAll();
  delay(15);

  char hexBuf[5];
  snprintf(hexBuf, sizeof(hexBuf), "%x", unicode);
  for (int j = 0; hexBuf[j] != '\0'; j++) {
    keyboard.write(hexBuf[j]);
    delay(5);
  }
  delay(10);
  keyboard.write(KEY_RETURN);
  delay(15);
}

void sendAltCode(uint16_t code) {
  keyboard.press(KEY_LEFT_ALT);
  delay(10);

  char digits[5];
  snprintf(digits, sizeof(digits), "%04u", code);

  for (int i = 0; i < 4; i++) {
    uint8_t kp_key = KEY_KP_0;
    if (digits[i] >= '1' && digits[i] <= '9') {
      kp_key = KEY_KP_1 + (digits[i] - '1');
    }
    keyboard.press(kp_key);
    delay(6);
    keyboard.release(kp_key);
    delay(6);
  }

  keyboard.release(KEY_LEFT_ALT);
  delay(10);
}

void sendABNT2Accent(uint16_t unicode) {
  switch (unicode) {
    // --- TIL (~) -> Tecla física '\'' no US HID ---
    case 227: sendDeadKeyAccent('\'', 'a'); break; // ã
    case 195: sendDeadKeyAccent('\'', 'A'); break; // Ã
    case 245: sendDeadKeyAccent('\'', 'o'); break; // õ
    case 213: sendDeadKeyAccent('\'', 'O'); break; // Õ
    case 241: sendDeadKeyAccent('\'', 'n'); break; // ñ
    case 209: sendDeadKeyAccent('\'', 'N'); break; // Ñ

    // --- AGUDO (´) -> Tecla física '[' no US HID ---
    case 225: sendDeadKeyAccent('[', 'a'); break; // á
    case 193: sendDeadKeyAccent('[', 'A'); break; // Á
    case 233: sendDeadKeyAccent('[', 'e'); break; // é
    case 201: sendDeadKeyAccent('[', 'E'); break; // É
    case 237: sendDeadKeyAccent('[', 'i'); break; // í
    case 205: sendDeadKeyAccent('[', 'I'); break; // Í
    case 243: sendDeadKeyAccent('[', 'o'); break; // ó
    case 211: sendDeadKeyAccent('[', 'O'); break; // Ó
    case 250: sendDeadKeyAccent('[', 'u'); break; // ú
    case 218: sendDeadKeyAccent('[', 'U'); break; // Ú

    // --- CIRCUNFLEXO (^) -> Tecla física '"' (Shift + '\'') no US HID ---
    case 226: sendDeadKeyAccent('"', 'a'); break; // â
    case 194: sendDeadKeyAccent('"', 'A'); break; // Â
    case 234: sendDeadKeyAccent('"', 'e'); break; // ê
    case 202: sendDeadKeyAccent('"', 'E'); break; // Ê
    case 244: sendDeadKeyAccent('"', 'o'); break; // ô
    case 212: sendDeadKeyAccent('"', 'O'); break; // Ô

    // --- CRASE (`) -> Tecla física '{' (Shift + '[') no US HID ---
    case 224: sendDeadKeyAccent('{', 'a'); break; // à
    case 192: sendDeadKeyAccent('{', 'A'); break; // À

    // --- CEDILHA (ç) -> Tecla física ';' no US HID ---
    case 231: keyboard.write(';'); break; // ç
    case 199: keyboard.write(':'); break; // Ç

    default:
      sendLinuxUnicode(unicode);
      break;
  }
}

void sendTextUTF8(String text) {
  int len = text.length();
  int i = 0;
  while (i < len) {
    uint8_t c = (uint8_t)text[i];
    if (c < 128) {
      // Caractere ASCII padrão (0-127)
      keyboard.write(c);
      i++;
    } else if ((c & 0xE0) == 0xC0 && i + 1 < len) {
      // Sequência UTF-8 de 2 bytes (Acentos: ã, Ã, ç, Ç, á, é, í, ó, ú, â, ê, ô, à, õ, etc.)
      uint8_t c2 = (uint8_t)text[i + 1];
      uint16_t unicode = ((c & 0x1F) << 6) | (c2 & 0x3F);

      if (accentMode == 0) {
        // --- MODO 0: DEAD KEYS ABNT2 (Teclado Português do Brasil) ---
        sendABNT2Accent(unicode);
      } else if (accentMode == 1) {
        // --- MODO 1: LINUX UNICODE (Ctrl+Shift+U) ---
        sendLinuxUnicode(unicode);
      } else {
        // --- MODO 2: WINDOWS ALT CODE ---
        sendAltCode(unicode);
      }
      i += 2;
    } else if ((c & 0xF0) == 0xE0 && i + 2 < len) {
      i += 3;
    } else if ((c & 0xF8) == 0xF0 && i + 3 < len) {
      i += 4;
    } else {
      i++;
    }
  }
}

void processSingleCommand(String cmd) {
  cmd.trim();
  if (cmd.length() == 0) return;

  // Clean redundant prefixes if present in command
  while (cmd.startsWith("CMD:") || cmd.startsWith("KEY:") || cmd.startsWith("COMBO:")) {
    if (cmd.startsWith("CMD:") ) cmd = cmd.substring(4);
    else if (cmd.startsWith("KEY:")) cmd = cmd.substring(4);
    else if (cmd.startsWith("COMBO:")) cmd = cmd.substring(6);
    cmd.trim();
  }

  if (cmd.length() == 0) return;

  if (cmd.startsWith("ACCENT:")) {
    accentMode = cmd.substring(7).toInt();
  } else if (cmd == "ACCENT_DEADKEY") {
    accentMode = 0;
  } else if (cmd == "ACCENT_LINUX") {
    accentMode = 1;
  } else if (cmd == "ACCENT_ALTCODE") {
    accentMode = 2;
  } else if (cmd == "MOUSE_CLICK" || cmd == "MOUSE_LCLICK") {
    Mouse.click(MOUSE_LEFT);
  } else if (cmd == "MOUSE_RCLICK") {
    Mouse.click(MOUSE_RIGHT);
  } else if (cmd == "COPY") {
    processKeyCombination("CTRL+c");
  } else if (cmd == "PASTE") {
    processKeyCombination("CTRL+v");
  } else if (cmd == "CHROME") {
    processKeyCombination("WIN+r");
    delay(300);
    keyboard.print("chrome");
    delay(100);
    keyboard.write(KEY_RETURN);
    delay(50);
    keyboard.releaseAll();
  } else if (cmd.startsWith("MOVE:") || cmd.startsWith("MOVE_HUMAN:") || cmd.startsWith("HUMAN_MOVE:") ||
             cmd.startsWith("MOVE_TO:") || cmd.startsWith("MOVE_ABS:") || cmd.startsWith("MOUSE_MOVE:") || cmd.startsWith("MOUSE:")) {
    bool isHuman = (cmd.startsWith("MOVE_HUMAN:") || cmd.startsWith("HUMAN_MOVE:"));
    int colonIndex = cmd.indexOf(':');
    String paramsStr = cmd.substring(colonIndex + 1);
    paramsStr.trim();

    int targetX = 0, targetY = 0, speed = 5;

    int c1 = paramsStr.indexOf(',');
    if (c1 != -1) {
      targetX = paramsStr.substring(0, c1).toInt();
      int c2 = paramsStr.indexOf(',', c1 + 1);
      if (c2 == -1) {
        targetY = paramsStr.substring(c1 + 1).toInt();
      } else {
        targetY = paramsStr.substring(c1 + 1, c2).toInt();
        int c3 = paramsStr.indexOf(',', c2 + 1);
        if (c3 == -1) {
          speed = paramsStr.substring(c2 + 1).toInt();
          isHuman = true;
        } else {
          speed = paramsStr.substring(c2 + 1, c3).toInt();
          int mode = paramsStr.substring(c3 + 1).toInt();
          isHuman = (mode != 0);
        }
      }
      moveMouseTo(targetX, targetY, speed, isHuman);
    }
  } else if (cmd.startsWith("TEXT:") || cmd.startsWith("TXT:")) {
    int prefixLen = cmd.startsWith("TEXT:") ? 5 : 4;
    sendTextUTF8(cmd.substring(prefixLen));
  } else if (cmd.startsWith("DELAY:")) {
    int d = cmd.substring(6).toInt();
    if (d > 0 && d <= 10000) delay(d);
  } else {
    // Key combination (e.g. CTRL+F4) or individual key (e.g. TAB, ENTER, ESC)
    processKeyCombination(cmd);
  }
}

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) override {
      String rxValue = pCharacteristic->getValue();

      if (rxValue.length() > 0) {
        Serial.print("Received: ");
        Serial.println(rxValue);

        if (rxValue.startsWith("TXT:")) {
          String text = rxValue.substring(4);
          sendTextUTF8(text);
        } else if (rxValue.startsWith("CMD:")) {
          String cmd = rxValue.substring(4);
          processSingleCommand(cmd);
        } else if (rxValue.startsWith("SEQ:")) {
          String seq = rxValue.substring(4);
          int startIndex = 0;
          while (startIndex < seq.length()) {
            int endIndex = seq.indexOf('\n', startIndex);
            if (endIndex == -1) endIndex = seq.length();

            String cmd = seq.substring(startIndex, endIndex);
            cmd.trim();

            if (cmd.length() > 0) {
              processSingleCommand(cmd);
              delay(50);
            }
            startIndex = endIndex + 1;
          }
        }

        // --- SEND RESPONSE TO MOBILE APP ---
        if (pTxCharacteristic != nullptr) {
          String resposta = "OK! Received: " + rxValue;
          pTxCharacteristic->setValue(resposta.c_str());
          pTxCharacteristic->notify();
          Serial.println("Response sent to mobile app.");
        }
      }
    }
};

void setup() {
  Serial.begin(115200);

  USB.productName("Mouse USB HID");
  USB.manufacturerName("HID Device");
  keyboard.begin();
  Mouse.begin();
  USB.begin();

  BLEDevice::init("ESP32-S3-UART");

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // TX Characteristic (Notification with BLE2902 descriptor required by Android)
  pTxCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID_TX,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
  pTxCharacteristic->addDescriptor(new BLE2902());

  // RX Characteristic (Write from mobile app)
  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
                                           CHARACTERISTIC_UUID_RX,
                                           BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
                                         );
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06); // Recommended parameters for Android/iOS compatibility
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("BLE Advertising (Bluedroid) started for service: " SERVICE_UUID);
  Serial.println("Ready! Connect via nRF Connect or Android App.");
}

unsigned long lastTempUpdate = 0;

void loop() {
  if (millis() - lastTempUpdate > 5000) {
    lastTempUpdate = millis();
    float temp_celsius = temperatureRead();
    Serial.print("Internal Chip Temperature: ");
    Serial.println(temp_celsius);

    if (pServer != nullptr && pServer->getConnectedCount() > 0 && pTxCharacteristic != nullptr) {
      String msg = "TEMP:" + String(temp_celsius, 2) + "C";
      pTxCharacteristic->setValue(msg.c_str());
      pTxCharacteristic->notify();
    }
  }
}
