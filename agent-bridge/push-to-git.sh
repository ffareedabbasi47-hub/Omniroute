#!/data/data/com.termux/files/usr/bin/bash
set -e
export PATH="$HOME/.local/bin:/data/data/com.termux/files/usr/bin:$PATH"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "📦 Adding modified files to Git..."
git add -A

echo "📝 Committing changes..."
git commit -m "Fix camera photo upload, thinking tag leaks, and enable image/video generation with vision analysis" || echo "No changes to commit or already committed."

echo "🚀 Pushing to GitHub (origin main)..."
git push origin main

echo "✅ Git push completed successfully!"
