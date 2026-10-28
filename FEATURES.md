# ORBIT AI – Feature status (detailed)

## Implemented
- Kotlin native app, minSdk 29, Material dark navy + cyan UI
- Conversation UI with user / assistant / system bubbles
- Circular mic FAB + text input
- Online AI via configurable OpenAI-compatible HTTP API
- Encrypted storage for API key and HA token
- Offline rule-based command parser
- TTS (TextToSpeech) spoken replies
- One-shot speech recognition (button activated)
- **Optional “Hey Orbit” wake word** via foreground microphone service (Settings toggle)
- Open installed apps by name/package
- Open system settings pages (Wi‑Fi, Bluetooth, etc.)
- Open URLs and web search
- Flashlight toggle
- Speak current time
- Call intent with confirmation dialog
- SMS prepare (user must send)
- AccessibilityService for optional automation
- “Record my steps” / “Stop recording” / “Repeat …”
- Save, list, rename, delete routines
- Emergency stop control
- Device list UI + manual add + HA connectivity probe
- Backup/restore routines via Storage Access Framework (JSON schema v1)
- Settings screen for AI, search, HA, TTS, offline preference, wake word
- GitHub Actions workflow producing debug APK artifact
- Unit tests for CommandParser and routine JSON schema

## Partially implemented
- Step replay: works for OPEN_APP + text/id-based taps when UI is stable; stops on mismatch
- Floating overlay bubble service (needs user overlay permission)
- Notification listener service (enable in system settings; no auto-read yet)
- Smart-home: configuration + stubs; real entity control needs your HA mapping
- Network device discovery: extension point only (no full mDNS library bundled)
- Wake word: uses platform SpeechRecognizer in a loop (not a dedicated offline DSP model). Works best with screen on and battery unrestricted.

## Planned / not implemented in this release
- True low-power offline DSP wake engine (Porcupine-style)
- Arbitrary Bluetooth GATT product control
- Full cross-app pixel-perfect macro recording
- Local offline speech recognition model download
- Manufacturer-specific smart light/plug plugins beyond HA

Do not assume unimplemented items work. Enable only the permissions and services you need.
