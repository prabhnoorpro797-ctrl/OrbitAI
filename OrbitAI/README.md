# ORBIT AI

Futuristic JARVIS-style Android assistant for Samsung Galaxy S24 (and other Android 10+ devices).

**Package:** `com.orbitai.assistant`  
**Language:** Kotlin  
**Min SDK:** 29 (Android 10)

---

## Feature status

| Feature | Status | Notes |
|--------|--------|-------|
| Online AI chat (OpenAI-compatible) | **Implemented** | Configurable endpoint + encrypted API key |
| Offline commands (apps, settings, time, flashlight) | **Implemented** | Rule-based parser |
| Speech recognition (button) | **Implemented** | Platform SpeechRecognizer |
| Text-to-speech replies | **Implemented** | Android TTS |
| Wake-word "Hey Orbit" | **Implemented (optional)** | Settings toggle; foreground mic service + notification |
| Open apps / settings / URLs / search | **Implemented** | Intents |
| Phone calls (with confirmation) | **Implemented** | Needs CALL_PHONE |
| Prepare SMS (user sends) | **Implemented** | Does not auto-send |
| Flashlight | **Implemented** | Camera torch API |
| Accessibility step recorder ("Copy my steps") | **Partial** | Records window + click events; replay best-effort |
| Routine save / list / rename / delete | **Implemented** | Local JSON |
| Routine replay | **Partial** | Needs Accessibility Service; fails safely on UI change |
| Emergency stop | **Implemented** | UI button + service interrupt |
| Floating bubble | **Partial** | Service present; needs overlay permission |
| Wi-Fi device discovery | **Partial** | Manual add + optional Home Assistant hub check |
| Bluetooth control | **Planned** | Permissions declared; no arbitrary GATT control |
| Smart home (HA) | **Partial** | Config + scan stub; map entities yourself |
| Notification reading | **Partial** | Listener service; enable in system settings |
| Backup / restore routines | **Implemented** | SAF; never exports API keys |
| GitHub Actions debug APK | **Implemented** | `.github/workflows/build-apk.yml` |
| Unit tests (parser + routines) | **Implemented** | `app/src/test` |

Honest limits: full arbitrary screen recording/replay across all apps is **not** claimed. “Hey Orbit” uses a visible foreground service (not a silent DSP chip). Smart devices require your own hubs/APIs.

---

## Phone-only: build & install APK

You only need your phone + a free GitHub account.

### 1. Upload project to GitHub

1. On your phone, open a browser (Chrome recommended).
2. Go to [github.com](https://github.com) and sign in (or create an account).
3. Tap **+** → **New repository**.
4. Name it `OrbitAI`, set **Public**, create.
5. On the empty repo page, look for **uploading an existing file** (or use the mobile-friendly “Add file” → “Upload files”).
6. Unzip the OrbitAI ZIP on your phone (Files app / Samsung My Files).
7. Select **all** files and folders inside the project root (the folder that contains `app/`, `build.gradle.kts`, `.github/`, etc.) and upload them.
8. Commit.

> Tip: If the ZIP is large, upload from a PC once, or use the GitHub mobile app / a Git client that supports Android.

### 2. Run the build workflow

1. In the GitHub repo, open the **Actions** tab.
2. Select **Build Debug APK**.
3. Tap **Run workflow** → **Run workflow**.
4. Wait 3–8 minutes until the green check appears.

### 3. Download the APK

1. Open the completed workflow run.
2. Scroll to **Artifacts**.
3. Download **orbit-ai-debug-apk**.
4. Unzip the artifact; you will get an `.apk` file.

### 4. Install on Galaxy S24

1. Open **Settings → Security and privacy → Install unknown apps** (or **Apps → Special access → Install unknown apps**).
2. Allow your browser / Files app to install.
3. Open the APK and install.
4. Open **ORBIT AI**.

### 5. First-run setup

1. Grant **Microphone**, **Camera** (flashlight), and other prompts as needed.
2. Open the menu → **Settings**.
3. Enter your AI API key (OpenAI or any compatible endpoint). Key is stored **encrypted**.
4. Optionally set Home Assistant URL + token.
5. For “Copy my steps”:  
   **Settings → Accessibility → Installed apps → ORBIT AI** → enable the service.
6. For floating bubble: grant **Display over other apps** when prompted / from system settings.

### 6. Try commands

- “What time is it?”
- “Turn on flashlight”
- “Open YouTube”
- “Search weather”
- “Record my steps” → do actions → “Stop recording” → “Repeat …”

### Enable “Hey Orbit” wake word

1. Open **ORBIT AI → menu → Settings**.
2. Turn on **Hey Orbit wake word (foreground listening)**.
3. Allow **Microphone** and **Notifications** if asked.
4. A persistent notification appears: *ORBIT AI — Hey Orbit / Listening…*
5. Say **“Hey Orbit”** (or “Hi Orbit” / “Orbit”). The app opens and starts listening for your command.
6. Tap **Stop listening** on the notification (or turn the switch off in Settings) when finished.

**Samsung Galaxy S24 – make it more reliable**

| Setting | Where |
|--------|--------|
| Microphone | Allowed for ORBIT AI (already prompted) |
| Notifications | Allowed (Android 13+) so the listening bar stays visible |
| Battery | **Settings → Apps → ORBIT AI → Battery → Unrestricted** |
| Sleeping apps | **Settings → Battery → Background usage limits → Never sleeping apps → Add ORBIT AI** |

**Limits (honest):** This is not Google Assistant–level always-on DSP. It runs short speech-recognition sessions while a foreground notification is showing. It uses more battery. Reliability drops if the phone is in deep sleep or the OS kills background mic use. The mic button always works as a fallback.

---

## Termux + USB install

Full scripts live in the `termux/` folder. Short version:

1. Put **OrbitAI.apk** on the USB (build via GitHub Actions first).
2. On the phone, install Termux → `pkg install termux-tools`.
3. Plug USB and run:

```bash
for d in /storage/usb* /mnt/media_rw/* /storage/* /sdcard; do f=$(find $d -name 'OrbitAI.apk' 2>/dev/null | head -1); [ -n "$f" ] && termux-open "$f" && break; done
```

Or: `bash termux/setup-alias.sh` once, then always type `orbitinstall`.

See `termux/README-TERMUX.md` for Linux/Windows helpers.

---

## Local development (if you later use a PC)

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Security

- API keys and HA tokens use **EncryptedSharedPreferences**.
- Backups exclude secrets.
- Sensitive actions (calls) ask for confirmation.
- Accessibility and notification access are **user-enabled** and optional.
- No hidden recording.

---

## License

Provided as-is for personal use. Review permissions before installing.
