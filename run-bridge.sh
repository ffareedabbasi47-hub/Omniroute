#!/data/data/com.termux/files/usr/bin/bash
export PATH="$HOME/.local/bin:/data/data/com.termux/files/usr/bin:$PATH"
[ -f "$HOME/.local/bin/agy" ] && chmod +x "$HOME/.local/bin/agy" 2>/dev/null

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR/agent-bridge" || exit 1

# Clean up any stale or previous bridge instance
fuser -k 8787/tcp >/dev/null 2>&1 || pkill -f "python3.*bridge.py" >/dev/null 2>&1
sleep 0.5

echo "🚀 Starting fresh Antigravity Bridge for OmniAssistant on http://127.0.0.1:8787..."
exec python3 bridge.py "$@"
