# Requirements matrix

| # | Requirement | Implemented | Verified |
|---|---|---|---|
| 1 | OmniRoute base URL / key / model settings | Yes | NOT TESTED |
| 2 | Streaming chat (SSE) | Yes | NOT TESTED (parser unit-tested in CI only) |
| 3 | Model discovery (`GET /models`) | Yes | NOT TESTED |
| 4 | Room history (v2, migration) | Yes | NOT TESTED |
| 5 | Hindi/Hinglish/English, voice in/out | Yes (via model + Android STT/TTS) | NOT TESTED |
| 6 | Photo / video / file attachments | Yes | NOT TESTED |
| 7 | Big-AI-app style UI (dark violet, orb, bubbles, terminal agent) | Yes | NOT TESTED |
| 8 | Coding agent via Claude Code bridge: plan, approve, edit, diff, approved commands | Yes | Bridge PASS (fake claude); app NOT TESTED |
| 9 | Secrets redacted, allowlisted commands, localhost bind | Yes | PASS in bridge tests |
| 10 | GitHub Actions APK build | Workflow written | NOT TESTED (needs a push) |
| 11 | Image generation | Yes (Create image mode) | NOT TESTED |
| 12 | Video generation | Yes, experimental | NOT TESTED / UNVERIFIED schema |
| 13 | File generation (PDF/MD/TXT/code files) | Yes (on-device export) | NOT TESTED |
| 14 | Editable system prompt | Yes | NOT TESTED |
