#!/bin/bash

echo "=================================="
echo " Buscando dispositivos ESP32 BLE "
echo "=================================="
echo "Isso pode levar alguns segundos..."

# Limpar o cache do bluetoothctl (opcional, pode ajudar a ver dispositivos novos)
# bluetoothctl remove 'MAC'

# Usar timeout para executar o comando scan do bluetoothctl por 10 segundos
timeout 10 bluetoothctl scan on > /tmp/ble_scan_result.txt

# Também podemos ver os dispositivos já descobertos
bluetoothctl devices >> /tmp/ble_scan_result.txt

echo -e "\n--- Resultados Encontrados ---"

# Buscar por "ESP32" no arquivo gerado
if grep -i "ESP32" /tmp/ble_scan_result.txt; then
    echo -e "\n✅ SUCESSO: Foi encontrado pelo menos um dispositivo com 'ESP32' no nome."
    echo "Isso indica que o Bluetooth do seu ESP32 está funcionando e transmitindo."
else
    echo -e "\n❌ AVISO: Nenhum dispositivo 'ESP32' encontrado."
    echo "Por favor verifique:"
    echo "1. Se o ESP32 está ligado na energia."
    echo "2. Se o código carregado no ESP32 realmente inicializa o BLE e faz o 'advertising'."
    echo "3. Se o nome do BLE no seu código contém a palavra 'ESP32'."
fi

# Apagar arquivo temporário
rm /tmp/ble_scan_result.txt
