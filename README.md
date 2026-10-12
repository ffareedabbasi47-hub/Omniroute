# Omni Assistant

Android AI assistant (Kotlin, Jetpack Compose, Material 3) with two separate parts:

1. **Chat** with any OpenAI-compatible server such as **OmniRoute** (streaming, history, voice).
2. **Coding agent**: send tasks to **Claude Code** through a small bridge you run yourself. You approve every plan, edit and command.

> Status: written without access to the Android SDK, so the APK has **not been compiled yet**. The GitHub Actions workflow in this repo does the first real build (see below). The Python bridge *was* run against 13 automated tests.

## Build the APK with GitHub Actions

1. Create a new GitHub repository and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab. The workflow **Build debug APK** runs on every push (or press *Run workflow*).
3. When it is green, open the run and download the artifact **omni-assistant-debug-apk** (contains `app-debug.apk`).
4. Install it on your phone (allow "install unknown apps" for your browser/file manager).

If the run fails, open the failed step and send me the red error lines; the code has not been through a compiler yet, so a few small fixes are possible.

The workflow runs the unit tests first (`:app:testDebugUnitTest`), then `:app:assembleDebug`. It uses Gradle 8.7 installed by `gradle/actions/setup-gradle`, so no Gradle wrapper file is needed. The debug APK is signed with the standard Android debug key; do not publish it. For a release build you would add your own keystore as GitHub secrets (never commit it).

## Connect OmniRoute

Open the app, then **Settings → OmniRoute connection**:

| Field | Value |
|---|---|
| Base URL | Your OmniRoute address ending in `/v1`, e.g. `http://127.0.0.1:20128/v1` if OmniRoute runs on the same phone (its documented default port is 20128) or `http://<computer-ip>:20128/v1` on your network |
| API key | Your OmniRoute endpoint key. Leave empty if your server has `REQUIRE_API_KEY` off |
| Model | **Choose model** loads the list from `GET /models`; or type a model name |

The app calls `GET {base}/models` and `POST {base}/chat/completions` with `Authorization: Bearer <key>` and `stream: true`. If the server has no `/models`, the app says "not verified" instead of pretending it is connected.

**About the key:** it is stored encrypted (Android Keystore + EncryptedSharedPreferences) and never logged. That keeps it from other apps, but anyone who controls your unlocked phone can still use it. Use a key meant for this phone, not a master key.

Plain `http://` to another machine sends your key and chats unencrypted; the app warns you. Use `https://` or a VPN such as Tailscale for anything beyond your own home network.

## Coding agent (Claude Code)

An Android app cannot directly run a Termux command, and this app does not try to bypass that. Instead:

```
Omni Assistant app  --HTTP + bearer token-->  agent-bridge/bridge.py  --runs-->  claude (Claude Code CLI)
```

The bridge runs where Claude Code is installed (Termux on the same phone, or a PC). Requirements: Python 3.8+, Git, and the `claude` CLI already logged in.

### Set up the bridge

```bash
cd agent-bridge
cp config.example.json config.json     # edit: your project folders and allowed commands
python3 bridge.py
```

On first start it prints a **token** (also saved in `agent-bridge/.bridge-token`). In the app: **Settings → Coding-agent bridge**, enter the bridge URL (`http://127.0.0.1:8787` when running in Termux on the same phone) and the token, then *Save and test bridge*.

`config.json`:

```json
{
  "workspaces": { "my-project": "~/projects/my-project" },
  "commands": [ {"id": "test", "label": "Run unit tests", "argv": ["./gradlew", "testDebugUnitTest"]} ]
}
```

### What happens when you run a task

1. **Plan** – Claude Code runs in *plan mode* with only `Read, Glob, Grep`. It cannot edit or run anything. You read the plan.
2. **Approve and apply** – only after you tap approve, Claude Code runs with `Read, Glob, Grep, Edit, Write` (shell, web and notebook tools are denied) inside the chosen project.
3. **Review** – the app shows `git status` and the `git diff`.
4. **Verify** – tests/builds run only from the `commands` list in `config.json`, never from model output, each behind an approval dialog showing the exact command. The app reports the real exit code (PASSED / FAILED / TIMED OUT).

Safety properties: token required on every request; listens on `127.0.0.1` unless you pass `--allow-network`; the app can only name projects by label (never paths); one task per project at a time; timeouts (15 min default) and a Cancel button; secrets are redacted from logs; request size is limited. Not an OS sandbox: it relies on Claude Code's own permission system plus the tool allowlist, so run it as a normal user, not root. Undoing edits is done with git on the bridge machine; the app never runs destructive git commands.

Run the bridge tests: `python3 agent-bridge/test_bridge.py` (uses a fake `claude`, no network).

Please verify once on your machine that your installed Claude Code accepts the flags used (`-p`, `--output-format stream-json --verbose`, `--permission-mode plan|acceptEdits`, `--allowedTools`, `--disallowedTools`). If your version differs, adjust `start_claude_task` in `bridge.py`.

## Voice

Mic button in the chat box (speech-to-text), a **Voice conversation** screen (speak → send → reply read aloud), and a speaker button on each reply. Uses Android's SpeechRecognizer and TextToSpeech, so Hindi/Hinglish support depends on the speech services and voices installed on your phone. Microphone permission is requested only when you tap the mic. Whether the model itself answers well in Hindi or Hinglish depends on the model you select.

## Project layout

- `app/src/main/java/com/omni/assistant/`
  - `net/` OmniRoute client (SSE streaming, errors, redaction), bridge client
  - `data/` Room database (conversations, messages), secure settings
  - `voice/` speech input and output
  - `ui/` Compose screens: onboarding, chat, history, settings, model picker, voice, agent
- `agent-bridge/` Python bridge and its tests
- `.github/workflows/build-apk.yml` APK build

## Known limitations

- Not yet compiled or run on a device (see Status). UI layout and voice features are untested on real hardware.
- No cancellation guarantee beyond closing the connection; some servers keep generating server-side.
- Markdown support is a small subset (code blocks, headings, lists, bold, inline code); tables and links render as plain text.
- Release signing, Play Store packaging and Termux auto-start of the bridge are not set up.


## Attachments (v2)
Tap **+** in the composer: camera, photos/videos, or files (PDF, text, code). Images are downscaled and sent as `image_url` parts; videos are sent as up to 6 sampled frames (**no audio**); text files are inlined; PDFs are sent as a `file` part, which not every backend supports. The selected model must be vision-capable. Only the last two attachment messages are re-sent in full.

## Status
The Android code has **not been compiled yet** (no SDK access where it was written). Push to GitHub, open Actions, and fix any compile errors from the log. See `docs/ENGINEERING_STATUS.md`.

## Generation and files (v3)
- **Create image / Create video** chips above the composer call `/images/generations` and `/videos/generations` on your server using the models set in Settings. Results are saved privately, shown in the chat, and can be shared/saved with the share button. Image models can be discovered via `/models/image`. Video is experimental (the server's response format is undocumented).
- **Files:** any reply can be saved as PDF, Markdown or text (download icon); each code block has a share icon that saves it as a file (e.g. `.py`, `.kt`). Files are produced on the phone from the model's text; the model itself does not return binary documents.
- **System prompt:** Settings > Assistant instructions (editable, resettable).
- Generation needs a provider/model that actually supports it; otherwise the app shows the server's error.
