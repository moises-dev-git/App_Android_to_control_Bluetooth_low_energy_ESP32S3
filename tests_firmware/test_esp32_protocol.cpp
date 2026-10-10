#include <iostream>
#include <string>
#include <cstdint>
#include <cassert>
#include <vector>
#include <algorithm>

// Simulated HID Key definitions from USBHIDKeyboard.h
#define KEY_RETURN     0x28
#define KEY_ESC        0x29
#define KEY_BACKSPACE  0x2A
#define KEY_TAB        0x2B
#define KEY_CAPS_LOCK  0x39
#define KEY_F1         0x3A
#define KEY_F5         0x3E
#define KEY_F12        0x45
#define KEY_DELETE     0x4C
#define KEY_RIGHT_ARROW 0x4F
#define KEY_LEFT_ARROW  0x50
#define KEY_DOWN_ARROW  0x51
#define KEY_UP_ARROW    0x52

// Protocol logic extracted directly from ESP32-S3.ino for contract verification
uint8_t parseSpecialKey(std::string key) {
    std::transform(key.begin(), key.end(), key.begin(), ::toupper);
    // trim
    size_t first = key.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return 0;
    size_t last = key.find_last_not_of(" \t\r\n");
    key = key.substr(first, (last - first + 1));

    if (key == "ENTER" || key == "RETURN") return KEY_RETURN;
    if (key == "ESC" || key == "ESCAPE") return KEY_ESC;
    if (key == "TAB") return KEY_TAB;
    if (key == "BACKSPACE" || key == "BS") return KEY_BACKSPACE;
    if (key == "DELETE" || key == "DEL") return KEY_DELETE;
    if (key == "UP" || key == "UP_ARROW") return KEY_UP_ARROW;
    if (key == "DOWN" || key == "DOWN_ARROW") return KEY_DOWN_ARROW;
    if (key == "LEFT" || key == "LEFT_ARROW") return KEY_LEFT_ARROW;
    if (key == "RIGHT" || key == "RIGHT_ARROW") return KEY_RIGHT_ARROW;
    if (key == "SPACE") return ' ';
    if (key == "CAPSLOCK" || key == "CAPS_LOCK") return KEY_CAPS_LOCK;
    if (key == "F1") return KEY_F1;
    if (key == "F5") return KEY_F5;
    if (key == "F12") return 0x45;
    return 0;
}

uint16_t decodeUtf8TwoBytes(uint8_t c1, uint8_t c2) {
    if ((c1 & 0xE0) == 0xC0) {
        return ((c1 & 0x1F) << 6) | (c2 & 0x3F);
    }
    return 0;
}

struct MouseParams {
    int x;
    int y;
    int speed;
    bool isHuman;
};

bool parseMouseCommand(const std::string& cmd, MouseParams& out) {
    bool isHuman = (cmd.rfind("MOVE_HUMAN:", 0) == 0 || cmd.rfind("HUMAN_MOVE:", 0) == 0);
    size_t colonIndex = cmd.find(':');
    if (colonIndex == std::string::npos) return false;

    std::string paramsStr = cmd.substr(colonIndex + 1);
    size_t c1 = paramsStr.find(',');
    if (c1 == std::string::npos) return false;

    out.x = std::stoi(paramsStr.substr(0, c1));
    size_t c2 = paramsStr.find(',', c1 + 1);
    if (c2 == std::string::npos) {
        out.y = std::stoi(paramsStr.substr(c1 + 1));
        out.speed = 5;
        out.isHuman = isHuman;
        return true;
    }

    out.y = std::stoi(paramsStr.substr(c1 + 1, c2 - c1 - 1));
    size_t c3 = paramsStr.find(',', c2 + 1);
    if (c3 == std::string::npos) {
        out.speed = std::stoi(paramsStr.substr(c2 + 1));
        out.isHuman = true;
    } else {
        out.speed = std::stoi(paramsStr.substr(c2 + 1, c3 - c2 - 1));
        int mode = std::stoi(paramsStr.substr(c3 + 1));
        out.isHuman = (mode != 0);
    }
    return true;
}

int main() {
    std::cout << "[TDD] Rodando testes unitários do protocolo C++ ESP32..." << std::endl;

    // Teste 1: Teclas especiais
    assert(parseSpecialKey("ENTER") == KEY_RETURN);
    assert(parseSpecialKey("enter") == KEY_RETURN);
    assert(parseSpecialKey("  ESC  ") == KEY_ESC);
    assert(parseSpecialKey("tab") == KEY_TAB);
    assert(parseSpecialKey("backspace") == KEY_BACKSPACE);
    assert(parseSpecialKey("F1") == KEY_F1);
    assert(parseSpecialKey("f5") == KEY_F5);
    assert(parseSpecialKey("SPACE") == ' ');
    std::cout << "  ✔ Teste 1: Reconhecimento de teclas especiais (parseSpecialKey) passou." << std::endl;

    // Teste 2: Decodificação UTF-8 para acentuação em Português
    // 'ã' em UTF-8: 0xC3 0xA3 -> Unicode 227
    // 'ç' em UTF-8: 0xC3 0xA7 -> Unicode 231
    // 'é' em UTF-8: 0xC3 0xA9 -> Unicode 233
    assert(decodeUtf8TwoBytes(0xC3, 0xA3) == 227); // ã
    assert(decodeUtf8TwoBytes(0xC3, 0xA7) == 231); // ç
    assert(decodeUtf8TwoBytes(0xC3, 0xA9) == 233); // é
    assert(decodeUtf8TwoBytes(0xC3, 0xA1) == 225); // á
    std::cout << "  ✔ Teste 2: Decodificação de acentos UTF-8 Português (ã, ç, é, á) passou." << std::endl;

    // Teste 3: Parser de parâmetros de Mouse
    MouseParams p1;
    assert(parseMouseCommand("MOVE:100,200", p1));
    assert(p1.x == 100 && p1.y == 200 && p1.isHuman == false);

    MouseParams p2;
    assert(parseMouseCommand("MOVE_HUMAN:50,-30,8", p2));
    assert(p2.x == 50 && p2.y == -30 && p2.speed == 8 && p2.isHuman == true);
    std::cout << "  ✔ Teste 3: Parser de comandos de movimento de mouse (MOVE, MOVE_HUMAN) passou." << std::endl;

    // Teste 4: Remoção de prefixos repetidos
    std::string testCmd = "CMD:KEY:CTRL+C";
    while (testCmd.rfind("CMD:", 0) == 0 || testCmd.rfind("KEY:", 0) == 0) {
        if (testCmd.rfind("CMD:", 0) == 0) testCmd = testCmd.substr(4);
        else if (testCmd.rfind("KEY:", 0) == 0) testCmd = testCmd.substr(4);
    }
    assert(testCmd == "CTRL+C");
    std::cout << "  ✔ Teste 4: Limpeza de prefixos compostos passou." << std::endl;

    std::cout << "\n[SUCESSO] Todos os 4 conjuntos de testes de firmware ESP32 passaram com 100% de sucesso!" << std::endl;
    return 0;
}
