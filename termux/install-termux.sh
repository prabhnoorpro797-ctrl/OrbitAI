#!/data/data/com.termux/files/usr/bin/bash
# ORBIT AI – install APK from USB (or any storage) via Termux
# Usage: bash install-termux.sh
#    or: bash install-termux.sh /path/to/OrbitAI.apk

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}=== ORBIT AI Termux Installer ===${NC}"

# Prefer explicit path if given
APK="${1:-}"

if [ -z "$APK" ]; then
  echo "Searching for OrbitAI.apk ..."
  # Common USB / storage mount points on Android
  SEARCH_PATHS=(
    /storage/usb*
    /mnt/media_rw/*
    /storage/*
    /sdcard
    /storage/emulated/0
    "$HOME"
  )
  for pattern in "${SEARCH_PATHS[@]}"; do
    # shellcheck disable=SC2086
    found=$(find $pattern -name 'OrbitAI.apk' -o -name 'app-debug.apk' -o -name '*orbit*ai*.apk' 2>/dev/null | head -1)
    if [ -n "$found" ]; then
      APK="$found"
      break
    fi
  done
fi

if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo -e "${RED}OrbitAI.apk not found.${NC}"
  echo "Put OrbitAI.apk on the USB or phone storage, or pass the path:"
  echo "  bash install-termux.sh /path/to/OrbitAI.apk"
  exit 1
fi

echo -e "${GREEN}Found:${NC} $APK"
ls -lh "$APK"

# Ensure termux-open exists
if ! command -v termux-open >/dev/null 2>&1; then
  echo "Installing termux-tools ..."
  pkg install -y termux-tools
fi

# Copy to shared storage so the system installer can always read it
DEST="/storage/emulated/0/Download/OrbitAI.apk"
mkdir -p /storage/emulated/0/Download 2>/dev/null || true
if cp "$APK" "$DEST" 2>/dev/null; then
  echo "Copied to $DEST"
  OPEN_TARGET="$DEST"
else
  OPEN_TARGET="$APK"
fi

echo -e "${CYAN}Opening system installer… tap Install when prompted.${NC}"
termux-open "$OPEN_TARGET"

echo ""
echo -e "${GREEN}Done.${NC} After install:"
echo "  1. Open ORBIT AI"
echo "  2. Allow Microphone (+ Notifications for Hey Orbit)"
echo "  3. Settings → Apps → ORBIT AI → Battery → Unrestricted"
echo "  4. In-app: menu → Settings → API key + Hey Orbit toggle"
echo ""
echo "Tip: add alias with:  bash setup-alias.sh"
