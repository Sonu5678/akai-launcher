#!/data/data/com.termux/files/usr/bin/bash
# Undo: re-enables everything disable-app.sh switched off.
#   bash scripts/restore.sh <tv-ip>            re-enable apps
#   bash scripts/restore.sh <tv-ip> --remove   also uninstall this launcher
PKG=com.mylauncher.akaitv
TV="$1"
if [ -z "$TV" ]; then echo "Usage: bash scripts/restore.sh <tv-ip> [--remove]"; exit 1; fi
case "$TV" in *:*) ADDR="$TV" ;; *) ADDR="$TV:5555" ;; esac
BK="$HOME/tv-backup"
adb connect "$ADDR" >/dev/null 2>&1 || true

if [ -f "$BK/disabled-by-me.txt" ]; then
  sort -u "$BK/disabled-by-me.txt" | while read -r P; do
    [ -n "$P" ] && adb -s "$ADDR" shell pm enable "$P"
  done
else
  echo "Nothing recorded in $BK/disabled-by-me.txt"
fi

if [ "$2" = "--remove" ]; then
  adb -s "$ADDR" uninstall $PKG
fi
adb -s "$ADDR" shell input keyevent KEYCODE_HOME
echo "Restored."
