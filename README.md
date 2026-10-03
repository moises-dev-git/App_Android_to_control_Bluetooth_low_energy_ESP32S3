# ESP32-S3 BLE HID Controller & Android App

Este projeto consiste em uma solução completa para controle remoto de um computador utilizando um ESP32-S3 configurado como dispositivo de Interface Humana (HID - Teclado e Mouse) via USB, e um aplicativo Android nativo que se comunica com o ESP32 via Bluetooth Low Energy (BLE).

---

## 🚀 Funcionalidades

- **Emulação de Teclado e Mouse USB:** O ESP32-S3 se comporta como teclado e mouse físicos USB (`Mouse USB HID`).
- **Combinações Dinâmicas de Teclas:** Suporte a qualquer combinação (`CTRL+SHIFT+ESC`, `ALT+F4`, `WIN+R`, `CTRL+ALT+DEL`, `WIN+D`, etc.), aceitando maiúsculas e minúsculas.
- **Mapeamento de Teclas Especiais:** Suporta `ENTER`, `ESC`, `TAB`, `DEL`/`DELETE`, `BACKSPACE`, `INSERT`, `HOME`, `END`, `PAGEUP`, `PAGEDOWN`, `CAPSLOCK`, setas direcionais e teclas de função `F1` a `F12`.
- **Movimento e Clique de Mouse:** Controle relativo de ponteiro (`dx`, `dy`) e cliques esquerdo/direito.
- **Execução de Macros Sequenciais (`SEQ:`):** Executa rotinas automáticas com atrasos (`DELAY:ms`), digitação de texto e atalhos.
- **Carrossel por Categorias no Android:** Interface do app com rolagem lateral organizada em 5 categorias para seleção de teclas.

---

## 📚 Bibliotecas Utilizadas no ESP32-S3

O firmware utiliza **apenas bibliotecas nativas inclusas no pacote oficial de placas da Espressif para Arduino** (não é necessário instalar nenhuma biblioteca externa de terceiros):

```cpp
#include "USB.h"             // Hardware da pilha USB nativa do ESP32-S3
#include "USBHIDKeyboard.h"   // Emulação nativa de Teclado USB HID
#include "USBHIDMouse.h"      // Emulação nativa de Mouse USB HID

#include <BLEDevice.h>        // Pilha oficial Bluedroid BLE da Espressif
#include <BLEServer.h>        // Criador do Servidor GATT BLE
#include <BLEUtils.h>         // Conversões de dados e UUIDs
#include <BLE2902.h>          // Descritor Obrigatório 0x2902 (CCCD) para Android
```

> **Por que o descritor `BLE2902` é obrigatório?**  
> O sistema operacional Android bloqueia e descarta transmissões de características BLE com notificação (`PROPERTY_NOTIFY`) que não possuam o descritor `BLE2902` registrado. Sua inclusão garante que o Android reconheça o ESP32-S3.

---

## ⚙️ Configurações Obrigatórias da Arduino IDE

No menu **Ferramentas** (*Tools*) da Arduino IDE, selecione exatamente as seguintes opções antes de fazer o upload do firmware:

| Configuração na IDE | Opção a Selecionar | Explicação Técnica |
| :--- | :--- | :--- |
| **Placa (Board)** | `ESP32S3 Dev Module` | Placa de desenvolvimento oficial para ESP32-S3. |
| **Esquema de Partição (Partition Scheme)** | **`Huge APP (3MB No OTA / 1MB SPIFFS)`** | **OBRIGATÓRIO:** A pilha Bluetooth + USB HID ocupa mais de 1.3 MB. A partição padrão estoura a memória. A opção *Huge APP* libera 3 MB inteiros para a aplicação. |
| **USB CDC On Boot** | `Enabled` | Permite visualizar a saída do `Serial.println()` no Monitor Serial. |
| **USB Mode** | `Hardware CDC and JTAG` *(ou USB-OTG TinyUSB)* | Ativa a emulação nativa do Teclado e Mouse USB. |
| **Core ESP32** | `esp32` por Espressif Systems (v2.0.x ou v3.x) | Gerenciador de placas oficial da Espressif na Arduino IDE. |

---

## 📱 Requisitos do Celular Android

* **Localização / GPS LIGADO:** O sistema Android exige que o GPS esteja **ativado** na barra de notificações para permitir varreduras por dispositivos BLE.
* **Permissões de Dispositivos Próximos / Bluetooth:** Conceder as permissões solicitadas na abertura do aplicativo.

---

## 📂 Arquivos do Repositório

* [`ESP32-S3.ino`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/ESP32-S3.ino): Código fonte do firmware para o ESP32-S3.
* [`App Android/`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android): Projeto Android Studio em Java nativo.
* [`documentacao_projeto.md`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/documentacao_projeto.md): Documentação técnica completa da arquitetura e comandos BLE.
* [`historico_alteracoes.md`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/historico_alteracoes.md): Histórico completo de correções e alterações.
