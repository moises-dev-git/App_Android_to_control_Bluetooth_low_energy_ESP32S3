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
* **Suporte a Movimentação Direta e Humanizada do Ponteiro do Mouse (`MOVE:x,y` e `MOVE_HUMAN:x,y,speed`):**
  - Implementada a função `moveMouseTo(targetX, targetY, speed, isHuman)`.
  - **Modo Direto (`MOVE:x,y`):** Reseta para (0,0) em alta velocidade e move em passos retos.
  - **Modo Humanizado (`MOVE_HUMAN:x,y,speed` ou `MOVE:x,y,speed,1`):** Reseta com curva/desaceleração até (0,0) e descreve uma **Curva de Bézier Quadrática** até `(x, y)` com algoritmo de aceleração/desaceleração natural (**Ease-In-Out**). Permite definir a velocidade de 1 (lento) a 10 (rápido).
* **Resolução do Problema de Descoberta Bluetooth no Android:**
  - Uso da biblioteca oficial Espressif `BLEDevice.h` (Bluedroid), inclusão do descritor `BLE2902` (CCCD) e proteção contra crash loop no `loop()`.
* **Suporte Universal a Caracteres Acentuados no Linux Mint e Windows (`sendTextUTF8`):**
  - Implementados 3 Modos de Acentuação comutáveis em tempo de execução via comandos `CMD:ACCENT_DEADKEY`, `CMD:ACCENT_LINUX` ou `CMD:ACCENT_ALTCODE`:
    1. **Modo 0 (Dead Keys - Padrão):** Simula teclas mortas (`~` + `a` $\rightarrow$ `ã`, `'` + `a` $\rightarrow$ `á`, `'` + `c` $\rightarrow$ `ç`). Funciona perfeitamente no Linux Mint e Windows em teclados ABNT2 e US-International.
    2. **Modo 1 (Linux GTK Unicode):** Envia o atalho nativo do Linux Mint (`Ctrl+Shift+U` + Código Hex + `Enter`). Funciona no Linux Mint com **qualquer** layout de teclado configurado.
    3. **Modo 2 (Windows Alt Code):** Envia a combinação `Alt` + Teclado Numérico `0XXX` para sistemas Windows.

---

### 2. Aplicativo Android

* **Carrossel Horizontal por Categorias em `MacroActivity`:**
  - Implementados carrosséis de rolagem lateral (**HorizontalScrollView**) na tela de Gerenciamento de Macros organizados em 5 categorias elegantes e não poluídas:
    1. ⚡ **Atalhos Rápidos:** `Ctrl+Z`, `Ctrl+C`, `Ctrl+V`, `Ctrl+Shift+Esc`, `Alt+F4`, `Win+D`.
    2. 🎹 **Edição & Controle:** `Enter`, `Tab`, `Esc`, `Space`, `Backspace`, `Delete`, `Insert`, `Home`, `End`, `PageUp`, `PageDown`.
    3. 🎯 **Navegação & Setas:** `↑ Up`, `↓ Down`, `← Left`, `→ Right`, `Caps Lock`.
    4. 🛠️ **Teclas F1 a F12:** `F1`, `F2`, `F3`, `F4`, `F5`, `F6`, `F7`, `F8`, `F9`, `F10`, `F11`, `F12`.
    5. 🖱️ **Ações & Comandos:** `TEXT:`, `DELAY:500`, `Move Direto`, `Move Humano`, `Click Esq.`, `Click Dir.`.
* **Expansão de MTU de 512 Bytes ([`ControlActivity.java`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/ControlActivity.java)):** Solicita `requestMtu(512)` ao conectar.
* **Varredura BLE Ultra-Rápida e Alerta de GPS ([`MainActivity.java`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/MainActivity.java)).**
* **Navegação e Rolagem Suave na Caixa de Texto de Macros ([`MacroActivity.java`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/MacroActivity.java) / [`activity_macro.xml`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/res/layout/activity_macro.xml)):**
  - Implementada intercepção de gestos de toque (`requestDisallowInterceptTouchEvent(true)`), permitindo deslizar o dedo na caixa de texto para rolar a macro de forma direta e fluida, **sem precisar abrir o teclado ou arrastar a agulha do cursor**.
  - Adicionada barra de rolagem vertical visível contínua (`android:scrollbars="vertical"`).
  - Incluídos botões de navegação rápida **`⬆️ Topo`** e **`⬇️ Fim`** no cabeçalho do campo para salto instantâneo no início ou fim de macros longas.
* **Migração para Jetpack Compose & Material Design 3:**
  - O aplicativo Android foi completamente modernizado com a toolkit **Jetpack Compose** e **Material 3**.
  - Implementado tema escuro cyberpunk/IoT moderno ([`Theme.kt`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/ui/theme/Theme.kt), [`Color.kt`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/ui/theme/Color.kt)).
  - Atualizadas as telas principais para componentes reativos em Kotlin ([`MainActivity.kt`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/MainActivity.kt), [`ControlActivity.kt`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/ControlActivity.kt), [`MacroActivity.kt`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android/app/src/main/java/com/example/esp32app/MacroActivity.kt)), mantendo 100% da compatibilidade e lógica Bluetooth LE/GATT e salvamento de macros.

---

## ⚙️ Configuração da Arduino IDE para Compilação

Ao compilar o firmware no ESP32-S3, selecione:
* **Placa:** `ESP32S3 Dev Module`
* **Partition Scheme:** `Huge APP (3MB No OTA / 1MB SPIFFS)`
* **USB CDC On Boot:** `Enabled`
* **USB Mode:** `Hardware CDC and JTAG`
