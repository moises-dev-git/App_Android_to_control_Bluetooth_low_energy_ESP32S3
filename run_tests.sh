#!/bin/bash
set -e

echo "=========================================================="
echo "   Executando Suíte de Testes TDD (Android & ESP32)       "
echo "=========================================================="

echo -e "\n[1/2] Executando testes unitários do Android (Gradle)..."
cd "App Android"
./gradlew test --quiet
echo "✔ Testes do Android concluídos com sucesso!"
cd ..

echo -e "\n[2/2] Compilando e executando testes do Firmware ESP32 (C++)..."
g++ -O2 tests_firmware/test_esp32_protocol.cpp -o tests_firmware/test_esp32_protocol
./tests_firmware/test_esp32_protocol
echo "✔ Testes do Firmware concluídos com sucesso!"

echo -e "\n=========================================================="
echo "✅ TODOS OS TESTES PASSARAM COM 100% DE SUCESSO!"
echo "=========================================================="
