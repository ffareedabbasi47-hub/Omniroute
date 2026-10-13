#!/data/data/com.termux/files/usr/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR/agent-bridge" || exit 1
echo "🚀 Starting Antigravity Bridge for OmniAssistant on http://127.0.0.1:8787..."
python3 bridge.py "$@"
