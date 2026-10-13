#!/data/data/com.termux/files/usr/bin/bash
set -e
export PATH="$HOME/.local/bin:/data/data/com.termux/files/usr/bin:$PATH"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "📦 Adding modified files to Git..."
git add -A

echo "📝 Committing changes..."
git commit -m "Fix camera photo capture, prevent Activity recreation drop, and add memory-safe image decoding" || echo "No changes to commit or already committed."

echo "🚀 Pushing to GitHub (origin main)..."
git push origin main

echo "✅ Git push completed successfully!"
