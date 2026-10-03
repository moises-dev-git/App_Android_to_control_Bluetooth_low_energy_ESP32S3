#!/bin/bash

# ==============================================================================
# GitHub Update Script - ESP32-S3 BLE HID Controller
# ==============================================================================

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}  Updating GitHub Repository                        ${NC}"
echo -e "${CYAN}====================================================${NC}"

# Check if inside git repository
if [ ! -d ".git" ]; then
    echo -e "${RED}Error: This directory is not a Git repository.${NC}"
    exit 1
fi

echo -e "\n${YELLOW}1. Checking modified files...${NC}"
git status -s

echo -e "\n${YELLOW}2. Staging changes (git add .)...${NC}"
git add .

# Default commit message
DEFAULT_MSG="docs: translate repository documentation and firmware comments to English"

if [ -n "$1" ]; then
    COMMIT_MSG="$1"
else
    echo -e "\n${CYAN}Default commit message:${NC}"
    echo -e "\"$DEFAULT_MSG\""
    echo -e "\n${YELLOW}Press ENTER to accept or type a custom commit message:${NC}"
    read -r USER_MSG
    if [ -n "$USER_MSG" ]; then
        COMMIT_MSG="$USER_MSG"
    else
        COMMIT_MSG="$DEFAULT_MSG"
    fi
fi

echo -e "\n${YELLOW}3. Creating commit...${NC}"
git commit -m "$COMMIT_MSG"

echo -e "\n${YELLOW}4. Pushing changes to GitHub (git push origin main)...${NC}"
if git push origin main; then
    echo -e "\n${GREEN}====================================================${NC}"
    echo -e "${GREEN}  ✅ GitHub Repository successfully updated!        ${NC}"
    echo -e "${GREEN}====================================================${NC}"
else
    echo -e "\n${RED}====================================================${NC}"
    echo -e "${RED}  ❌ Failed to push to GitHub.                      ${NC}"
    echo -e "${RED}  Check your Git / GitHub credentials.              ${NC}"
    echo -e "${RED}====================================================${NC}"
fi
