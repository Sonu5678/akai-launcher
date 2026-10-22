#!/data/data/com.termux/files/usr/bin/bash
# Switches an app off (never uninstalls) and remembers it so restore.sh can undo it.
#   bash scripts/disable-app.sh <tv-ip> <package> [more packages]
TV="$1"; shift
if [ -z "$TV" ] || [ $# -eq 0 ]; then
  echo "Usage: bash scripts/disable-app.sh <tv-ip> <package> ..."; exit 1
fi
case "$TV" in *:*) ADDR="$TV" ;; *) ADDR="$TV:5555" ;; esac
BK="$HOME/tv-backup"; mkdir -p "$BK"
adb connect "$ADDR" >/dev/null 2>&1 || true
for P in "$@"; do
  # one pm command per call, on purpose
  if adb -s "$ADDR" shell pm disable-user --user 0 "$P"; then
    echo "$P" >> "$BK/disabled-by-me.txt"
  fi
done
