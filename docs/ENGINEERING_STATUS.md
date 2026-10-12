# Engineering status

Statuses: PASS / FAIL / BLOCKED / NOT TESTED. Nothing here is claimed beyond what was actually run.

| Area | Status | Evidence / note |
|---|---|---|
| Agent bridge (`agent-bridge/bridge.py`) | PASS | `python3 agent-bridge/test_bridge.py` – 13 tests, using a fake `claude` binary |
| Bridge with the real Claude Code CLI | NOT TESTED | CLI flags were checked against docs, never run end-to-end |
| Android compile (`assembleDebug`) | BLOCKED | Build sandbox had no access to Google/Maven/Gradle; code has never been compiled |
| Android unit tests (`testDebugUnitTest`) | NOT TESTED | Written (ApiParsing, Markdown, AttachmentRules) but not run; CI runs them |
| Debug APK | NOT BUILT | No APK exists locally. GitHub Actions builds it (`.github/workflows/build-apk.yml`) |
| Real OmniRoute server | NOT TESTED | Request format follows the OpenAI-compatible API; no live server was available |
| Streaming / models / history | NOT TESTED | Implemented, not run on a device |
| Attachments (photo/video/file) | NOT TESTED | Implemented; video is sent as sampled frames (no audio) |
| Vision / PDF support | NOT TESTED | Depends on the chosen model and backend |
| Voice (STT/TTS) | NOT TESTED | Uses Android SpeechRecognizer/TextToSpeech; needs a device |
| Image generation (`/images/generations`) | NOT TESTED | Request/response follow OmniRoute docs; parser unit-tested in CI only. Needs an image-capable model on your server |
| Video generation (`/videos/generations`) | NOT TESTED / UNVERIFIED | Path exists upstream, but request/response schema is undocumented; parser is tolerant, may fail on some providers |
| File generation / export | NOT TESTED | Reply -> PDF/Markdown/text via share sheet; code block -> file. Done on device, not by the server |
| Editable system prompt | NOT TESTED | Settings > Assistant instructions |
| UI redesign | NOT TESTED | Never rendered; compile errors and layout bugs are likely on first build |

## Known risks
- First CI run will probably surface Kotlin/Compose compile errors; fix from the Actions log.
- Cleartext http is allowed so a LAN/localhost OmniRoute works; the app warns on remote http.
- API key and bridge token are stored in EncryptedSharedPreferences; an unlocked device can still use them.
