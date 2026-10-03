# ESP32-S3 BLE HID Controller & Android App

Este projeto consiste em uma solução completa para controle remoto de um computador utilizando um ESP32-S3 configurado como dispositivo de Interface Humana (HID - Teclado e Mouse) via USB, e um aplicativo Android nativo que se comunica com o ESP32 via Bluetooth Low Energy (BLE).

---

## 🚀 Funcionalidades

- **Emulação de Teclado e Mouse USB:** O ESP32-S3 se comporta como teclado e mouse físicos USB.
- **Combinações Dinâmicas de Teclas:** Suporte a qualquer combinação (`CTRL+SHIFT+ESC`, `ALT+F4`, `WIN+R`, `CTRL+ALT+DEL`, `WIN+D`, etc.).
- **Mapeamento de Teclas Especiais:** Suporta `ENTER`, `ESC`, `TAB`, `DEL`/`DELETE`, `BACKSPACE`, `INSERT`, `HOME`, `END`, `PAGEUP`, `PAGEDOWN`, setas direcionais e teclas de função `F1` a `F12`.
- **Movimento e Clique de Mouse:** Controle relativo de ponteiro (`dx`, `dy`) e cliques esquerdo/direito.
- **Execução de Macros Sequenciais (`SEQ:`):** Executa rotinas automáticas com atrasos (`DELAY:ms`), digitação de texto e atalhos.
- **Leitura de Temperatura Interna:** Reporta a temperatura do chip ESP32-S3 ao celular apenas quando conectado.

---

## 📂 Estrutura do Projeto

1. **Firmware ESP32-S3 ([`ESP32-S3.ino`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/ESP32-S3.ino))**:
   - Desenvolvido para a Arduino IDE usando o core oficial Espressif ESP32.
   - Utiliza a pilha nativa **Bluedroid** com o descritor obrigatório **`BLE2902` (CCCD)** para compatibilidade total com o Android.
2. **Aplicativo Android ([`App Android/`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/App%20Android))**:
   - Desenvolvido em Java nativo para Android.
   - Escaneia em baixa latência (`SCAN_MODE_LOW_LATENCY`) e conecta ao ESP32-S3.
   - Possui interface intuitiva com criador de atalhos e construtor de macros.
3. **Documentação Detalhada ([`documentacao_projeto.md`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/documentacao_projeto.md))**:
   - Guia completo de arquitetura, protocolo, requisitos do Android e troubleshooting.
4. **Histórico de Alterações ([`historico_alteracoes.md`](file:///home/moises/.gemini/antigravity/scratch/ESP32-S3/historico_alteracoes.md))**:
   - Registro de todas as modificações efetuadas e guia para restauração de backups.

---

## ⚙️ Configurações da Arduino IDE (Upload no ESP32-S3)

No menu **Ferramentas** (*Tools*) da Arduino IDE, selecione obrigatoriamente:

* **Placa (Board):** `ESP32S3 Dev Module`
* **Partition Scheme:** **`Huge APP (3MB No OTA / 1MB SPIFFS)`** *(Obrigatório para comportar a pilha BLE + USB HID)*
* **USB CDC On Boot:** `Enabled`
* **USB Mode:** `Hardware CDC and JTAG`

---

## 📱 Requisitos do Celular Android

* **Localização / GPS LIGADO:** O sistema Android exige que o GPS esteja **ativado** na barra de notificações para permitir varreduras por dispositivos BLE.
* **Permissões de Dispositivos Próximos / Bluetooth:** Conceder todas as permissões solicitadas na primeira execução do aplicativo.
