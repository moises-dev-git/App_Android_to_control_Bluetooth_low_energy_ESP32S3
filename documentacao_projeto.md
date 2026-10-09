# Documentação Completa do Projeto: Controle HID via BLE com ESP32-S3

Este documento detalha toda a arquitetura, bibliotecas, requisitos do Android, configurações exatas da Arduino IDE e histórico de correções aplicadas para garantir que o **ESP32-S3** seja descoberto e funcione perfeitamente via **Bluetooth Low Energy (BLE)** e **USB HID**.

---

## 1. Visão Geral da Arquitetura

O projeto conecta um smartphone Android a um computador via ESP32-S3:
* **ESP32-S3 (Servidor BLE + Periférico USB HID):**
  * Conectado à porta USB do computador, identificando-se como **`Mouse USB HID`**.
  * Transmite sinal Bluetooth LE (BLE) anunciando o serviço UART Nordic (`6E400001-B5A3-F393-E0A9-E50E24DCCA9E`).
  * Recebe comandos via BLE vindos do celular e os injeta instantaneamente na porta USB do computador.
* **Aplicativo Android (Cliente BLE Central):**
  * Realiza o escaneamento de dispositivos BLE próximos com alta velocidade.
  * Configura a MTU para 512 bytes no momento da conexão, permitindo o envio de macros longas sem truncamento.
  * Fornece um **Carrossel de Categorias em Rolagem Lateral** na tela de Gerenciar Macros para inclusão de todas as teclas especiais (`F1-F12`, `Enter`, `Esc`, `Tab`, `Space`, `Backspace`, `Delete`, `Insert`, `Home`, `End`, `PageUp`, `PageDown`, `Setas`, `Caps Lock`, Atalhos e Comandos).

---

## 2. Nome do Dispositivo USB no Sistema Operacional (Windows vs Linux)

### 💻 Diferença entre `lsusb` no Linux e Gerenciador no Windows:
1. **No Linux (`lsusb`):**
   * O comando `lsusb` lê o código de fabricante VID (`0x303A`) e produto PID (`0x1001`) do hardware e pesquisa no banco local `/usr/share/hwdata/usb.ids`. Por isso exibe a string cadastrada no banco do sistema `Espressif Systems ESP32S3_DEV`.
   * Executando `lsusb -v`, é possível ver a string de descrição enviada pelo chip: `iProduct: Mouse USB HID`.
2. **No Windows (Gerenciador de Dispositivos e Dispositivos e Impressoras):**
   * O Windows **lê a string de descrição enviada pelo firmware do ESP32-S3** (`USB.productName("Mouse USB HID")`).
   * No Windows, o dispositivo é exibido na lista sob a categoria de *Dispositivos de Interface Humana (HID)* e *Teclados/Mouses* como **`Mouse USB HID`**.

---

## 3. Configurações Obrigatórias da Arduino IDE

Para compilar e carregar o firmware no **ESP32-S3** sem estouro de memória Flash/RAM ou travamentos na pilha de rádio Bluetooth:

### ⚙️ Opções do Menu `Ferramentas` (Tools) na Arduino IDE:

| Opção na IDE | Configuração Selecionada | Motivo / Explicação Técnica |
| :--- | :--- | :--- |
| **Placa (Board)** | `ESP32S3 Dev Module` | Placa de desenvolvimento com suporte ao chip ESP32-S3. |
| **Esquema de Partição (Partition Scheme)** | **`Huge APP (3MB No OTA / 1MB SPIFFS)`** | **CRÍTICO:** A biblioteca oficial Bluedroid BLE + USB HID ocupa mais de 1.3MB. A partição padrão (4MB Default) estoura a memória. A opção *Huge APP* disponibiliza 3MB livres para o programa. |
| **USB CDC On Boot** | `Disabled` | **Recomendado para HID Puro:** Desativa a porta COM pré-carregada na inicialização, permitindo aplicar o PID customizado (`0x8005`) e registrar o dispositivo como `Mouse USB HID`. |
| **USB Mode** | `USB-OTG (TinyUSB)` | Habilita a pilha software TinyUSB para controle total dos descritores de Teclado e Mouse USB. |
| **Core ESP32** | `esp32` por Espressif Systems (v2.0.x ou v3.x) | Pacote oficial de placas ESP32 para Arduino. |

> [!NOTE]
> **Comportamento na Arduino IDE (`Disconnected` / Sem Porta COM Visible):**
> Como a porta COM serial foi desativada no boot (`USB CDC On Boot = Disabled`) para liberar o PID customizado e a simulação pura de Teclado/Mouse HID, a placa aparecerá como **`disconnected`** ou sem porta COM na Arduino IDE durante o uso normal.
> 
> **Como gravar novos códigos na Arduino IDE:**
> 1. Pressione e segure o botão **`BOOT`** no ESP32-S3.
> 2. Pressione e solte o botão **`RESET`** (ou `EN`).
> 3. Solte o botão **`BOOT`** (a placa entra no modo Bootloader ROM da Espressif).
> 4. A porta serial reaparecerá temporariamente na IDE. Selecione a porta e clique em **Upload**.
> 5. Após a gravação terminar, pressione o botão **`RESET`** para voltar ao modo de execução normal (`Mouse USB HID`).

---

## 4. Bibliotecas do Firmware (`ESP32-S3.ino`)

Todas as bibliotecas utilizadas são **nativas do próprio pacote oficial da Espressif para Arduino**:

* `<USB.h>`: Gerenciador da pilha USB nativa do ESP32-S3.
* `<USBHIDKeyboard.h>`: Emulação de Teclado USB HID.
* `<USBHIDMouse.h>`: Emulação de Mouse USB HID.
* `<BLEDevice.h>`: Pilha oficial **Bluedroid** de Bluetooth Low Energy da Espressif.
* `<BLEServer.h>`: Criador do Servidor GATT BLE.
* `<BLEUtils.h>`: Conversões de dados e manipulação de UUIDs.
* `<BLE2902.h>`: **Descritor Obrigatório `0x2902` (CCCD)** para notificações no Android.

---

## 5. Interface do App Android & Carrossel por Categorias

A tela de Gerenciador de Macros foi construída utilizando **Carrosséis Horizontais de Rolagem Lateral (HorizontalScrollView)** para dar acesso a todas as teclas do firmware sem poluir visualmente a tela:

* ⚡ **Atalhos Rápidos:** `Ctrl+Z`, `Ctrl+C`, `Ctrl+V`, `Ctrl+Shift+Esc`, `Alt+F4`, `Win+D`.
* 🎹 **Edição & Controle:** `Enter`, `Tab`, `Esc`, `Space`, `Backspace`, `Delete`, `Insert`, `Home`, `End`, `PageUp`, `PageDown`.
* 🎯 **Navegação & Setas:** `↑ Up`, `↓ Down`, `← Left`, `→ Right`, `Caps Lock`.
* 🛠️ **Teclas F1 a F12:** `F1`, `F2`, `F3`, `F4`, `F5`, `F6`, `F7`, `F8`, `F9`, `F10`, `F11`, `F12`.
* 🖱️ **Ações & Comandos:** `TEXT:`, `DELAY:500`, `Click Esq.`, `Click Dir.`.

---

## 6. Estrutura de Comandos e Protocolo BLE

| Formato do Comando | Exemplo | Ação Executada no Computador |
| :--- | :--- | :--- |
| `TXT:texto` | `TXT:Hello World` | Digita o texto literalmente no computador. |
| `CMD:COPY` | `CMD:COPY` | Pressiona `Ctrl + c`. |
| `CMD:PASTE` | `CMD:PASTE` | Pressiona `Ctrl + v`. |
| `CMD:CHROME` | `CMD:CHROME` | Pressiona `Win + r`, digita "chrome" e aperta `Enter`. |
| `CMD:MOUSE_LCLICK` | `CMD:MOUSE_LCLICK` | Clique com o botão esquerdo do mouse. |
| `CMD:MOUSE_RCLICK` | `CMD:MOUSE_RCLICK` | Clique com o botão direito do mouse. |
| `CMD:MOVE:x,y` | `CMD:MOVE:500,300` | Movimento Direto: reseta para (0,0) e move em linha reta até (x, y). |
| `CMD:MOVE_HUMAN:x,y,speed` | `CMD:MOVE_HUMAN:500,300,5` | Movimento Humanizado: reseta suavemente para (0,0) e descreve uma Curva de Bézier com desaceleração e velocidade customizável (1-10). |
| `CMD:KEY:combinação` | `CMD:KEY:CTRL+SHIFT+ESC` | Pressiona a combinação dinâmica de teclas. |
| `SEQ:linhas` | `SEQ:MOVE_HUMAN:500,300,5\nDELAY:500\nENTER` | Executa uma sequência de comandos linha a linha (Macro). |

---

## 7. Suporte a Acentuação em Português (UTF-8 & Teclados ABNT2 / Linux / Windows)

Para permitir a digitação perfeita de caracteres acentuados (`ã`, `Ã`, `ç`, `Ç`, `á`, `é`, `í`, `ó`, `ú`, `â`, `ê`, `ô`, `à`, `õ`), o firmware inclui um decodificador UTF-8 (`sendTextUTF8`) e um mapeador de acentos ABNT2 (`sendABNT2Accent`):

* **Decodificação UTF-8:** Intercepta caracteres de 2 bytes (ex: `ã` = `0xC3 0xA3`) e extrai o código Unicode decimal.
* **Mapeamento ABNT2 Físico (Modo 0 - Padrão):** Mapeia os acentos para as posições físicas das teclas do teclado Português (Brasil ABNT2):
  * **Agudo (`´`):** Envia a tecla física `[` $\rightarrow$ gera `ó`, `á`, `é`, `í`, `ú`.
  * **Til (`~`):** Envia a tecla física `'` $\rightarrow$ gera `ã`, `Ã`, `õ`, `Õ`.
  * **Circunflexo (`^`):** Envia a tecla física `"` $\rightarrow$ gera `â`, `ê`, `ô`.
  * **Crase (`` ` ``):** Envia a tecla física `{` $\rightarrow$ gera `à`.
  * **Cedilha (`ç` / `Ç`):** Envia as teclas físicas `;` / `:` $\rightarrow$ gera `ç` / `Ç`.
* **Modos Alternativos de Acentuação:**
  * `CMD:ACCENT_DEADKEY` (ou `CMD:ACCENT:0`): Modo Teclas Mortas ABNT2 (Padrão para Linux Mint e Windows ABNT2).
  * `CMD:ACCENT_LINUX` (ou `CMD:ACCENT:1`): Modo Linux GTK Unicode (`Ctrl+Shift+U` + hex + `Enter`).
  * `CMD:ACCENT_ALTCODE` (ou `CMD:ACCENT:2`): Modo Windows Alt Codes (`Alt` + Numpad `0XXX`).

