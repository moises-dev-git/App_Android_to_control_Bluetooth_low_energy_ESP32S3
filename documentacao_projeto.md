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
| `CMD:ABS:x,y[,speed]` | `CMD:ABS:16383,8191` | **Mouse absoluto** (0..32767 em cada eixo = tela inteira). Posição exata, sem influência da aceleração do ponteiro. Com `speed` (1-10), faz curva de Bézier humanizada a partir da última posição absoluta. Usado pelo app em `CLICK_TEXT`/`CLICK_IMAGE`. |
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

---

## 8. Visão da Tela do PC (Captura HDMI + OCR + Busca de Imagens)

O app pode "enxergar" a tela do computador e tomar decisões durante a macro.

### 🔌 Montagem
```
PC ──HDMI──▶ Placa de captura UVC (ex.: MS2109) ──USB OTG──▶ Celular Android
PC ◀──USB── ESP32-S3 ◀──BLE── Celular Android
```
* O mouse absoluto do ESP32 é mapeado pelo sistema para a área de trabalho (no Linux/X11, a área inteira, somando todos os monitores). Com mais de um monitor, informe em **Painel → Captura HDMI → ícone de monitor** o tamanho total da área de trabalho e a posição/tamanho do monitor ligado à placa (ex.: principal 1920x1200 à esquerda e capturado 1920x1080 à direita → área 3840x1200, posição 1920,0, tamanho 1920x1080). Os botões **Canto/Centro** do diálogo movem o cursor para conferir. Sem essa calibração, `MOVE_`/`CLICK_TEXT` e `_IMAGE` acertam só com um único monitor ou em modo **"duplicar"**.
* Na primeira vez, abra **Painel → Visão**, conceda a permissão de câmera (exigida pela biblioteca UVC) e autorize o acesso USB à placa. Depois disso a captura liga sozinha ao abrir o Painel.

### 🧩 Componentes (pacote `com.example.esp32app.vision`)
| Componente | Função |
| :--- | :--- |
| `HdmiCapture` | Abre a placa UVC com a biblioteca `libausbc` (JitPack `com.github.ernestp.AndroidUSBCamera:3.6.0`), renderização fora da tela, e guarda o frame YUV mais recente (até 1920x1080). |
| `OcrEngine` | OCR com **ML Kit Text Recognition** (modelo latino embutido no APK, funciona **offline** e sem Google Play Services). |
| `TextMatcher` | Localiza o texto procurado (ignora maiúsculas, acentos e espaços) e devolve a caixa da palavra/trecho. |
| `ImageMatcher` + `TemplateStore` | Busca de modelos de imagem com **OpenCV** `matchTemplate` (similaridade mínima 0,80). Modelos ficam em `filesDir/templates` (PNG + resolução de origem, para reescalar se a captura mudar de resolução). |
| `MacroScript` / `MacroRunner` (pacote `macro`) | Parser e interpretador das macros com comandos de visão e controle de fluxo. |

### 📝 Comandos de Macro (executados no app)
Texto/imagem podem receber um **tempo limite opcional em ms** no final (padrão 10000): `WAIT_TEXT:Salvar,5000`.
Se o trecho após a última vírgula não for número, a vírgula faz parte do texto (`CLICK_TEXT:Sim, continuar`).

| Comando | Ação |
| :--- | :--- |
| `WAIT_TEXT:texto[,ms]` / `WAIT_NOT_TEXT:texto[,ms]` | Espera o texto aparecer / sumir. Se o tempo acabar, a macro é **interrompida**. |
| `WAIT_IMAGE:nome[,ms]` / `WAIT_NOT_IMAGE:nome[,ms]` | Idem para um modelo de imagem. |
| `CLICK_TEXT:texto[,ms]` / `CLICK_IMAGE:nome[,ms]` | Espera encontrar, move o mouse (absoluto) até o centro e clica com o botão esquerdo. |
| `MOVE_TEXT:texto[,ms]` / `MOVE_IMAGE:nome[,ms]` | Apenas move o mouse até o centro (combine com `MOUSE_RCLICK`, por exemplo). |
| `IF_TEXT:texto` / `IF_NOT_TEXT:texto` ... `ELSE` ... `END_IF` | Executa um bloco conforme o texto estar (ou não) na tela. Blocos podem ser aninhados. |
| `IF_IMAGE:nome` / `IF_NOT_IMAGE:nome` ... `ELSE` ... `END_IF` | Idem para modelos de imagem. |
| `REPEAT:n` ... `END_REPEAT` | Repete o bloco `n` vezes (1 a 10000). |
| `STOP` | Encerra a macro com sucesso. |
| `END_IF` / `END_REPEAT` | Fecham os blocos. `END` sozinho continua sendo a **tecla End**. |
| `// comentário` | Linha ignorada. |

Exemplo:
```
CHROME
WAIT_TEXT:Nova guia,8000
CTRL+L
TEXT:exemplo.com
ENTER
IF_TEXT:Aceitar cookies
  CLICK_TEXT:Aceitar cookies
END_IF
CLICK_IMAGE:botao_login,15000
```

### ⏱️ Sincronização
* O app espera a notificação `OK! Received: ...` do ESP32 (enviada **depois** que a ação HID termina) antes do próximo passo, com tolerância de 12 s.
* Antes de cada leitura de tela, o app aguarda ao menos 400 ms desde o último comando, para o PC redesenhar e o frame novo chegar pela placa de captura.
* A macro é validada ao salvar (blocos `IF_`/`REPEAT` sem `END_IF`/`END_REPEAT`, `ELSE` solto, tempos inválidos), e antes de executar o app confere se os modelos de imagem existem e se há imagem da captura.

