# ESP32-S3 BLE HID Controller & Android App

Este projeto consiste em uma solução completa para controle remoto de um computador utilizando um ESP32-S3 configurado como dispositivo de Interface Humana (HID - Teclado e Mouse) via USB, e um aplicativo Android nativo que se comunica com o ESP32 via Bluetooth Low Energy (BLE).

## Componentes do Projeto

1. **Firmware ESP32-S3 (`ESP32-S3.ino`)**:
   - Desenvolvido na Arduino IDE.
   - Atua como um teclado e mouse USB conectados a um computador.
   - Cria um servidor BLE com características de RX (recebimento de comandos) e TX (envio de respostas e temperatura).
   - Processa comandos recebidos do celular para controlar o mouse (cliques e movimentos), enviar atalhos de teclado (Copiar, Colar, abrir Chrome), enviar textos longos (Macros) e reportar a temperatura interna do chip a cada 5 segundos.

2. **Aplicativo Android (`App Android/`)**:
   - Aplicativo nativo desenvolvido em Java/Kotlin.
   - Escaneia e conecta ao ESP32-S3 via BLE.
   - Interface de controle para enviar comandos de teclado e mouse remotamente.
   - Gerenciador de Macros embutido.

## Comandos Suportados via BLE (Exemplos)

- **Comandos de Mouse**: `CMD:MOUSE_CLICK`, `CMD:MOUSE_RCLICK`, `CMD:MOUSE:dx,dy`
- **Atalhos e Textos**: `CMD:COPY`, `CMD:PASTE`, `CMD:CHROME`, `TXT:Hello World`
- **Sequências (Macros)**: O app pode enviar sequências contendo vários comandos, como `CTRL_F`, `ENTER`, `TAB`, `TEXT:...`, separados por quebras de linha usando o prefixo `SEQ:`.

## Como Utilizar

### ESP32-S3
1. Faça o upload do código `ESP32-S3.ino` para sua placa ESP32-S3 usando a IDE do Arduino.
2. Certifique-se de ter as partições configuradas adequadamente e as opções de USB ativadas para CDC/JTAG.
3. Conecte o ESP32-S3 via porta USB ao computador que deseja controlar.

### Aplicativo Android
1. Abra o projeto da pasta `App Android` no Android Studio.
2. Compile e instale no seu smartphone Android.
3. Ative o Bluetooth, abra o aplicativo, conecte-se ao dispositivo `ESP32-S3-UART` e comece a enviar os comandos usando a interface.
