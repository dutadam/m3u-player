#!/usr/bin/env bash
# cheesino web — tek komutla başlat (Node yeterli; ffmpeg varsa MKV/AVI de açılır).
cd "$(dirname "$0")"
URL="http://localhost:${PORT:-8088}"
node proxy.js &
PID=$!
sleep 1
# Tarayıcıyı otomatik aç (platforma göre).
( command -v xdg-open >/dev/null && xdg-open "$URL" ) 2>/dev/null || \
( command -v open >/dev/null && open "$URL" ) 2>/dev/null || \
( command -v start >/dev/null && start "$URL" ) 2>/dev/null || \
echo "Tarayıcıda açın: $URL"
wait $PID
