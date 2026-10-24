#!/data/data/com.termux/files/usr/bin/bash
# Sends your settings, wallpaper, font or show posters to the launcher. No rebuild needed.
#   bash scripts/config-push.sh <tv-ip>[:port] <file> [more files]
# The file name decides what it is: config.json, wallpaper.jpg, font.ttf, or any poster image.
PKG=com.mylauncher.akaitv
TV="$1"; shift
if [ -z "$TV" ] || [ $# -eq 0 ]; then
  echo "Usage: bash scripts/config-push.sh <tv-ip> <file> [more files]"; exit 1
fi
case "$TV" in *:*) ADDR="$TV" ;; *) ADDR="$TV:5555" ;; esac
a() { adb -s "$ADDR" "$@"; }
adb connect "$ADDR" >/dev/null 2>&1 || true

EXT="/sdcard/Android/data/$PKG/files"
FAIL=0
for SRC in "$@"; do
  if [ ! -f "$SRC" ]; then echo "No such file: $SRC"; FAIL=1; continue; fi
  NAME="$(basename "$SRC")"
  case "$SRC" in *config.json) NAME="config.json" ;; esac
  WANT="$(wc -c < "$SRC" | tr -d ' ')"
  OK=0
  if a push "$SRC" "$EXT/$NAME" >/dev/null 2>&1; then
    GOT="$(a shell stat -c%s "$EXT/$NAME" 2>/dev/null | tr -d '\r')"
    if [ "$GOT" = "$WANT" ]; then echo "pushed $NAME"; OK=1; fi
  fi
  if [ $OK -eq 0 ]; then
    # fallback: the app's private folder (works because this build is debuggable)
    if a exec-in "run-as $PKG sh -c 'mkdir -p files && cat > files/$NAME'" < "$SRC" 2>/dev/null; then
      GOT="$(a shell "run-as $PKG stat -c%s files/$NAME" 2>/dev/null | tr -d '\r')"
      if [ "$GOT" = "$WANT" ]; then echo "pushed $NAME (private folder)"; OK=1; fi
    fi
  fi
  if [ $OK -eq 0 ]; then echo "FAILED to push $NAME (size check did not match)"; FAIL=1; fi
done

# bring the launcher to the front so it reloads right away
a shell am start -n $PKG/.MainActivity >/dev/null 2>&1 || true
exit $FAIL
