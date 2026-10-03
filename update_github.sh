#!/bin/bash

# ==============================================================================
# Script de Atualização para o GitHub - ESP32-S3 BLE HID Controller
# ==============================================================================

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # Sem cor

echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}  Atualizando Repositório no GitHub                 ${NC}"
echo -e "${CYAN}====================================================${NC}"

# Verifica se está em um repositório git
if [ ! -d ".git" ]; then
    echo -e "${RED}Erro: Este diretório não é um repositório Git.${NC}"
    exit 1
fi

echo -e "\n${YELLOW}1. Verificando arquivos alterados...${NC}"
git status -s

echo -e "\n${YELLOW}2. Adicionando alterações (git add .)...${NC}"
git add .

# Mensagem de commit padrão caso o usuário não informe uma personalizada
DEFAULT_MSG="feat: Correções de descoberta BLE Android (descritor 0x2902), MTU 512, suporte a maiúsculas/minúsculas em atalhos, carrossel de categorias na tela de macros e nome USB HID"

if [ -n "$1" ]; then
    COMMIT_MSG="$1"
else
    echo -e "\n${CYAN}Mensagem de commit padrão:${NC}"
    echo -e "\"$DEFAULT_MSG\""
    echo -e "\n${YELLOW}Pressione ENTER para aceitar ou digite uma mensagem personalizada:${NC}"
    read -r USER_MSG
    if [ -n "$USER_MSG" ]; then
        COMMIT_MSG="$USER_MSG"
    else
        COMMIT_MSG="$DEFAULT_MSG"
    fi
fi

echo -e "\n${YELLOW}3. Criando commit...${NC}"
git commit -m "$COMMIT_MSG"

echo -e "\n${YELLOW}4. Enviando alterações para o GitHub (git push origin main)...${NC}"
if git push origin main; then
    echo -e "\n${GREEN}====================================================${NC}"
    echo -e "${GREEN}  ✅ Repositório atualizado no GitHub com SUCESSO!  ${NC}"
    echo -e "${GREEN}====================================================${NC}"
else
    echo -e "\n${RED}====================================================${NC}"
    echo -e "${RED}  ❌ Erro ao enviar para o GitHub.                  ${NC}"
    echo -e "${RED}  Verifique suas credenciais do Git / GitHub.        ${NC}"
    echo -e "${RED}====================================================${NC}"
fi
