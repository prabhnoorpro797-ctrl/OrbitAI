#!/bin/bash
# ORBIT AI – find APK on USB and copy to Downloads (Linux PC)
# Usage: bash install-linux.sh

set -e
echo "=== ORBIT AI (Linux) ==="

APK=$(find /media /mnt /run/media "$HOME" -name 'OrbitAI.apk' 2>/dev/null | head -1)

if [ -z "$APK" ]; then
  echo "OrbitAI.apk not found on mounted drives."
  echo "Place OrbitAI.apk on the USB or pass path as argument."
  exit 1
fi

mkdir -p "$HOME/Downloads"
cp "$APK" "$HOME/Downloads/OrbitAI.apk"
echo "Copied: $HOME/Downloads/OrbitAI.apk"
echo "This is an Android APK. Transfer it to a phone and install, or use an emulator."
command -v xdg-open >/dev/null && xdg-open "$HOME/Downloads" || true
