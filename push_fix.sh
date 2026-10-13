#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"
git add -A
git commit -m "Fix duplicate FileExport import and coroutineContext reference" || true
git push origin main
echo "SUCCESS: Code pushed to GitHub! Build #29 will start now."
