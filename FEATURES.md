# ORBIT AI — Feature checklist (v1.1)

## Implemented and wired
| Feature | Status | Notes |
|--------|--------|-------|
| Offline phone commands (apps, YT, Play Store, flashlight, time, battery) | Done | No API required |
| WhatsApp chat / call-assist | Done | Opens chat; user taps call icon |
| Multi-provider AI (OpenAI, Groq, xAI, Anthropic, Gemini, Ollama, Custom) | Done | Correct auth formats per provider |
| Secure multi-key storage | Done | EncryptedSharedPreferences per provider |
| Expert Panel (parallel multi-provider Q&A) | Done | Menu → Expert Panel |
| Room long-term memory + chat history | Done | Menu → Memory; “Remember that …” |
| Diagnostics + safe auto-repair | Done | Menu → Diagnostics |
| Voice input + TTS | Done | Mic button |
| Optional Hey Orbit foreground service | Done | Settings toggle |
| Routines / accessibility step recorder | Partial | Same as before |
| Home Assistant device probe | Partial | Config + scan stub |
| Floating bubble | Partial | Overlay permission required |
| Unit tests (parser, routines) | Done | `app/src/test` |

## Partial / constrained by Android
| Feature | Status |
|--------|--------|
| Continuous wake word with screen off | Limited by OS; uses foreground notification |
| Silent WhatsApp voice call start | Not allowed by WhatsApp/Android |
| Arbitrary Wi‑Fi/Bluetooth force toggles | Opens settings panels on modern Android |
| Local large LLM on-device | Use Ollama on a PC/server; point Custom/Ollama endpoint |

## Not claimed
- Silent always-on listening with app killed
- Fake smart-home controls without real backend
- Building APK inside this chat environment (use GitHub Actions)

## Groq free model tip
If you see `model_not_found`, set model to: **`llama-3.1-8b-instant`**
