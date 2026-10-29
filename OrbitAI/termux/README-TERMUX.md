# ORBIT AI – Termux & USB

The Android app still installs normally. Termux only **finds the APK and opens the installer**.

## On any Android phone with Termux

### One-time
```bash
pkg update -y && pkg install termux-tools -y
```

### Install from USB (short command)
Plug the pen drive, then in Termux:

```bash
for d in /storage/usb* /mnt/media_rw/* /storage/* /sdcard; do f=$(find $d -name 'OrbitAI.apk' 2>/dev/null | head -1); [ -n "$f" ] && termux-open "$f" && break; done
```

Or copy the `termux/` folder from the project/USB and run:

```bash
bash /path/to/termux/install-termux.sh
```

### Permanent short command
```bash
bash /path/to/termux/setup-alias.sh
source ~/.bashrc
```

Then every time:

```bash
orbitinstall
```

## USB folder layout (recommended)

```text
OrbitAI/
├── OrbitAI.apk              ← required (from GitHub Actions artifact)
├── OrbitAI-project.zip      ← optional
└── termux/
    ├── install-termux.sh
    ├── orbitinstall
    ├── setup-alias.sh
    ├── install-linux.sh
    ├── install-windows.bat
    └── README-TERMUX.md
```

## Computer

| OS | Command |
|----|---------|
| Linux | `bash termux/install-linux.sh` |
| Windows | double‑click `termux/install-windows.bat` |

Copies the APK to Downloads. You still install it on a phone.

## After APK install on phone

1. Open **ORBIT AI**
2. Allow **Microphone** (and **Notifications** for Hey Orbit)
3. **Settings → Apps → ORBIT AI → Battery → Unrestricted**
4. In app: **Settings → API key** + optional **Hey Orbit** switch

## Limits

- Termux cannot silently install apps without root.
- You must tap **Install** once on the system screen.
- The app does not run inside Termux; Termux only launches the installer.
