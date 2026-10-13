#!/data/data/com.termux/files/usr/bin/bash
export PATH="$HOME/.local/bin:/data/data/com.termux/files/usr/bin:$PATH"
[ -f "$HOME/.local/bin/agy" ] && chmod +x "$HOME/.local/bin/agy" 2>/dev/null

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR/agent-bridge" || exit 1

# If --restart or -r flag is passed, kill any existing process on port 8787
if [ "$1" = "--restart" ] || [ "$1" = "-r" ] || [ "$1" = "restart" ]; then
    echo "🔄 Stopping existing Antigravity Bridge..."
    fuser -k 8787/tcp >/dev/null 2>&1 || pkill -f "python3.*bridge.py" >/dev/null 2>&1
    sleep 1
    shift
fi

# Check if bridge is already listening on port 8787
if command -v fuser >/dev/null 2>&1 && fuser 8787/tcp >/dev/null 2>&1; then
    echo "⚠️ Antigravity Bridge is already running on http://127.0.0.1:8787"
    echo "To restart with latest code, run: bash run-bridge.sh --restart"
    exit 0
fi

echo "🚀 Starting Antigravity Bridge for OmniAssistant on http://127.0.0.1:8787..."
exec python3 bridge.py "$@"
