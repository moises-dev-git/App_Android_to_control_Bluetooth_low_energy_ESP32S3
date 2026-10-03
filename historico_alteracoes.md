# Histórico de Alterações e Guia de Restauração

Este documento registra o histórico completo de modificações realizadas no projeto **ESP32-S3 BLE HID Controller** e no **App Android**, detalhando os problemas resolvidos, configurações necessárias e procedimentos de backup/restauração.

---

## 📂 Backup Existente no Projeto
A pasta [`Cópias de segurança/`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/Cópias%20de%20segurança) contém a cópia original do projeto antes destas alterações:
- **Firmware Original**: [`Cópias de segurança/ESP32-S3 (cópia).ino`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/Cópias%20de%20segurança/ESP32-S3%20(cópia).ino)
- **App Android Original**: [`Cópias de segurança/App Android (cópia)/`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/Cópias%20de%20segurança/App%20Android%20(cópia))

---

## 📝 Resumo das Modificações Realizadas

### 1. Firmware do ESP32-S3 ([`ESP32-S3.ino`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/ESP32-S3.ino))

#### O que mudou:
* **Alteração do Nome do Produto USB para `"Mouse USB HID"`:**
  - Configurado `USB.productName("Mouse USB HID");` e `USB.manufacturerName("Dispositivo HID");` para exibição correta no Gerenciador de Dispositivos e Dispositivos/Impressoras do Windows.
* **Tratamento de Maiúsculas/Minúsculas em Atalhos (`CTRL+Z` vs `CTRL+z`):**
  - A função `processKeyCombination()` converte automaticamente qualquer letra de `'A'-'Z'` para `'a'-'z'` ao simular o scancode físico, impedindo que a biblioteca `USBHIDKeyboard` injete o `SHIFT` automaticamente em maiúsculas.
* **Suporte Total a Teclas Especiais (`parseSpecialKey`):**
  - Mapeadas teclas `ENTER`, `ESC`, `TAB`, `DEL`, `BACKSPACE`, `INSERT`, `HOME`, `END`, `PAGEUP`, `PAGEDOWN`, `UP`, `DOWN`, `LEFT`, `RIGHT`, `SPACE`, `CAPSLOCK` e `F1` até `F12`.
* **Resolução do Problema de Descoberta Bluetooth no Android:**
  - Uso da biblioteca oficial Espressif `BLEDevice.h` (Bluedroid), inclusão do descritor `BLE2902` (CCCD) e proteção contra crash loop no `loop()`.

---

### 2. Aplicativo Android

* **Carrossel Horizontal por Categorias em `MacroActivity`:**
  - Implementados carrosséis de rolagem lateral (**HorizontalScrollView**) na tela de Gerenciamento de Macros organizados em 5 categorias elegantes e não poluídas:
    1. ⚡ **Atalhos Rápidos:** `Ctrl+Z`, `Ctrl+C`, `Ctrl+V`, `Ctrl+Shift+Esc`, `Alt+F4`, `Win+D`.
    2. 🎹 **Edição & Controle:** `Enter`, `Tab`, `Esc`, `Space`, `Backspace`, `Delete`, `Insert`, `Home`, `End`, `PageUp`, `PageDown`.
    3. 🎯 **Navegação & Setas:** `↑ Up`, `↓ Down`, `← Left`, `→ Right`, `Caps Lock`.
    4. 🛠️ **Teclas F1 a F12:** `F1`, `F2`, `F3`, `F4`, `F5`, `F6`, `F7`, `F8`, `F9`, `F10`, `F11`, `F12`.
    5. 🖱️ **Ações & Comandos:** `TEXT:`, `DELAY:500`, `Click Esq.`, `Click Dir.`.
* **Expansão de MTU de 512 Bytes ([`ControlActivity.java`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/ControlActivity.java)):** Solicita `requestMtu(512)` ao conectar.
* **Varredura BLE Ultra-Rápida e Alerta de GPS ([`MainActivity.java`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/MainActivity.java)).**

---

## ⚙️ Configuração da Arduino IDE para Compilação

Ao compilar o firmware no ESP32-S3, selecione:
* **Placa:** `ESP32S3 Dev Module`
* **Partition Scheme:** `Huge APP (3MB No OTA / 1MB SPIFFS)`
* **USB CDC On Boot:** `Enabled`
* **USB Mode:** `Hardware CDC and JTAG`
