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
BLECharacteristic *pTxCharacteristic = nullptr; // Objeto para enviar dados

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
      Serial.println("Dispositivo Conectado!");
    }

    void onDisconnect(BLEServer* pServer) override {
      Serial.println("Dispositivo Desconectado! Reiniciando advertising...");
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

  // Substitui underlines por '+' se formato legado for usado (ex: CTRL_F4 -> CTRL+F4)
  if (comboStr.indexOf('+') == -1 && comboStr.indexOf('_') != -1) {
    comboStr.replace('_', '+');
  }

  // Garante liberação de teclas anteriores antes de iniciar nova combinação
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
        // Converte letras maiúsculas (A-Z) para minúsculas (a-z) ao simular o código físico da tecla.
        // Isso impede que a biblioteca USBHID insira a tecla SHIFT automaticamente quando a letra for maiúscula.
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
    delay(120); // Retardo de sustentação essencial para o SO (Windows/Linux) registrar o atalho USB HID
  }
  keyboard.releaseAll();
  delay(20);
}

void processSingleCommand(String cmd) {
  cmd.trim();
  if (cmd.length() == 0) return;

  // Limpa prefixos redundantes caso venham no comando
  while (cmd.startsWith("CMD:") || cmd.startsWith("KEY:") || cmd.startsWith("COMBO:")) {
    if (cmd.startsWith("CMD:")) cmd = cmd.substring(4);
    else if (cmd.startsWith("KEY:")) cmd = cmd.substring(4);
    else if (cmd.startsWith("COMBO:")) cmd = cmd.substring(6);
    cmd.trim();
  }

  if (cmd.length() == 0) return;

  if (cmd == "MOUSE_CLICK" || cmd == "MOUSE_LCLICK") {
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
  } else if (cmd.startsWith("MOUSE:")) {
    int commaIndex = cmd.indexOf(',', 6);
    if (commaIndex != -1) {
      int dx = cmd.substring(6, commaIndex).toInt();
      int dy = cmd.substring(commaIndex + 1).toInt();
      Mouse.move(dx, dy);
    }
  } else if (cmd.startsWith("TEXT:") || cmd.startsWith("TXT:")) {
    int prefixLen = cmd.startsWith("TEXT:") ? 5 : 4;
    keyboard.print(cmd.substring(prefixLen));
  } else if (cmd.startsWith("DELAY:")) {
    int d = cmd.substring(6).toInt();
    if (d > 0 && d <= 10000) delay(d);
  } else {
    // É uma combinação de teclas (ex: CTRL+F4) ou tecla individual (ex: TAB, ENTER, ESC)
    processKeyCombination(cmd);
  }
}

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) override {
      String rxValue = pCharacteristic->getValue();

      if (rxValue.length() > 0) {
        Serial.print("Recebido: ");
        Serial.println(rxValue);

        if (rxValue.startsWith("TXT:")) {
          String text = rxValue.substring(4);
          keyboard.print(text);
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

        // --- ENVIAR RESPOSTA PARA O CELULAR ---
        if (pTxCharacteristic != nullptr) {
          String resposta = "OK! Recebi: " + rxValue;
          pTxCharacteristic->setValue(resposta.c_str());
          pTxCharacteristic->notify();
          Serial.println("Resposta enviada ao app.");
        }
      }
    }
};

void setup() {
  Serial.begin(115200);

  USB.productName("Mouse USB HID");
  USB.manufacturerName("Dispositivo HID");
  keyboard.begin();
  Mouse.begin();
  USB.begin();

  BLEDevice::init("ESP32-S3-UART");

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Característica de TX (Notificação com descritor BLE2902 obrigatório para Android)
  pTxCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID_TX,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
  pTxCharacteristic->addDescriptor(new BLE2902());

  // Característica de RX (Escrita do celular)
  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
                                           CHARACTERISTIC_UUID_RX,
                                           BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
                                         );
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06); // Parâmetros recomendados para compatibilidade Android/iOS
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("BLE Advertising (Bluedroid) iniciado para o serviço: " SERVICE_UUID);
  Serial.println("Pronto! Conecte no nRF Connect ou App Android.");
}

unsigned long lastTempUpdate = 0;

void loop() {
  if (millis() - lastTempUpdate > 5000) {
    lastTempUpdate = millis();
    float temp_celsius = temperatureRead();
    Serial.print("Temperatura interna do chip: ");
    Serial.println(temp_celsius);

    if (pServer != nullptr && pServer->getConnectedCount() > 0 && pTxCharacteristic != nullptr) {
      String msg = "TEMP:" + String(temp_celsius, 2) + "C";
      pTxCharacteristic->setValue(msg.c_str());
      pTxCharacteristic->notify();
    }
  }
}
