#include "USBHIDKeyboard.h"
#include "USBHIDMouse.h"
#include "USB.h"

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

USBHIDKeyboard keyboard;
USBHIDMouse Mouse;


BLECharacteristic *pTxCharacteristic; // Objeto para enviar dados

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      String rxValue = pCharacteristic->getValue(); 

      if (rxValue.length() > 0) {
        Serial.print("Recebido: "); 
        Serial.println(rxValue);

        if (rxValue.startsWith("TXT:")) {
          String text = rxValue.substring(4);
          keyboard.print(text);
        } else if (rxValue.startsWith("CMD:")) {
          String cmd = rxValue.substring(4);
          
          if (cmd == "MOUSE_CLICK" || cmd == "MOUSE_LCLICK") {
            Mouse.click(MOUSE_LEFT);
          } else if (cmd == "MOUSE_RCLICK") {
            Mouse.click(MOUSE_RIGHT);
          } else if (cmd == "COPY") {
            keyboard.press(KEY_LEFT_CTRL);
            keyboard.press('c');
            delay(50);
            keyboard.releaseAll();
          } else if (cmd == "PASTE") {
            keyboard.press(KEY_LEFT_CTRL);
            keyboard.press('v');
            delay(50);
            keyboard.releaseAll();
          } else if (cmd == "CHROME") {
            keyboard.press(KEY_LEFT_GUI);
            keyboard.press('r');
            delay(100);
            keyboard.releaseAll();
            delay(300);
            keyboard.print("chrome");
            delay(100);
            keyboard.write(KEY_RETURN);
            delay(50);
            keyboard.releaseAll();
          } else if (cmd.startsWith("MOUSE:")) {
            // Format: CMD:MOUSE:dx,dy
            int commaIndex = cmd.indexOf(',', 6);
            if (commaIndex != -1) {
              int dx = cmd.substring(6, commaIndex).toInt();
              int dy = cmd.substring(commaIndex + 1).toInt();
              Mouse.move(dx, dy);
            }
          }
        }

        // --- ENVIAR RESPOSTA PARA O CELULAR ---
        String resposta = "OK! Recebi: " + rxValue;
        pTxCharacteristic->setValue(resposta.c_str());
        pTxCharacteristic->notify();
        Serial.println("Resposta enviada ao app.");
      }
    }
};

void setup() {
  Serial.begin(115200);
  

  USB.productName("Teclado/Mouse HID");
  USB.manufacturerName("Espressif");
  keyboard.begin();
  Mouse.begin();
  USB.begin();


  BLEDevice::init("ESP32-S3-UART");
  BLEServer *pServer = BLEDevice::createServer();
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Característica de TX (Notificação para o celular)
  pTxCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID_TX,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
  pTxCharacteristic->addDescriptor(new BLE2902()); // Necessário para notificações

  // Característica de RX (Escrita do celular)
  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
                                         CHARACTERISTIC_UUID_RX,
                                         BLECharacteristic::PROPERTY_WRITE
                                       );
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  pService->start();
  pServer->getAdvertising()->start();
  Serial.println("Pronto! Conecte no nRF Connect.");
}

void loop() {
  delay(1000);
}
