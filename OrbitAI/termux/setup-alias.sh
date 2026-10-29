#!/data/data/com.termux/files/usr/bin/bash
# Adds a permanent `orbitinstall` command in Termux
set -e

MARKER="# ORBIT AI installer"
SHELL_RC="$HOME/.bashrc"
[ -f "$HOME/.zshrc" ] && SHELL_RC="$HOME/.zshrc"

# Remove old block if present
if grep -q "$MARKER" "$SHELL_RC" 2>/dev/null; then
  # simple strip: rewrite without marker lines (portable)
  grep -v "$MARKER" "$SHELL_RC" | grep -v "orbitinstall" > "$SHELL_RC.tmp" || true
  mv "$SHELL_RC.tmp" "$SHELL_RC"
fi

cat >> "$SHELL_RC" << 'EOF'

# ORBIT AI installer
orbitinstall() {
  for d in /storage/usb* /mnt/media_rw/* /storage/* /sdcard /storage/emulated/0 "$HOME"; do
    f=$(find $d -name 'OrbitAI.apk' -o -name 'app-debug.apk' 2>/dev/null | head -1)
    if [ -n "$f" ]; then
      command -v termux-open >/dev/null 2>&1 || pkg install -y termux-tools
      cp "$f" /storage/emulated/0/Download/OrbitAI.apk 2>/dev/null || true
      termux-open "${f}"
      echo "Opened installer for: $f"
      return 0
    fi
  done
  echo "OrbitAI.apk not found on USB or storage."
  return 1
}
EOF

echo "Alias installed in $SHELL_RC"
echo "Run:  source $SHELL_RC"
echo "Then anytime USB is plugged:  orbitinstall"
source "$SHELL_RC" 2>/dev/null || true
