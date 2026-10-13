#!/data/data/com.termux/files/usr/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR/agent-bridge" || exit 1

# Check if bridge is already listening on port 8787
if command -v fuser >/dev/null 2>&1 && fuser 8787/tcp >/dev/null 2>&1; then
    echo "Antigravity Bridge is already running on http://127.0.0.1:8787"
    exit 0
fi

echo "🚀 Starting Antigravity Bridge for OmniAssistant on http://127.0.0.1:8787..."
exec python3 bridge.py "$@"
