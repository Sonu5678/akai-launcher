#!/data/data/com.termux/files/usr/bin/bash
# Sends your settings, wallpaper, font or show posters to the launcher. No rebuild needed.
#   bash scripts/config-push.sh <tv-ip>[:port] <file> [more files]
# The file name decides what it is: config.json, wallpaper.jpg, font.ttf, or any poster image.
#
# Files go into the launcher's own private folder (always readable by it). Any older copy in
# the shared folder is removed so it can never hide the new one.
PKG=com.mylauncher.akaitv
TV="$1"; shift
if [ -z "$TV" ] || [ $# -eq 0 ]; then
  echo "Usage: bash scripts/config-push.sh <tv-ip> <file> [more files]"; exit 1
fi
case "$TV" in *:*) ADDR="$TV" ;; *) ADDR="$TV:5555" ;; esac
a() { adb -s "$ADDR" "$@"; }
adb connect "$ADDR" >/dev/null 2>&1 || true
if ! a get-state >/dev/null 2>&1; then
  echo "Can't reach the TV at $ADDR. Check Wireless debugging is on and the port is right."; exit 1
fi

EXT="/sdcard/Android/data/$PKG/files"
TMP="/data/local/tmp"
FAIL=0
for SRC in "$@"; do
  if [ ! -f "$SRC" ]; then echo "No such file: $SRC"; FAIL=1; continue; fi
  NAME="$(basename "$SRC")"
  case "$SRC" in *config.json) NAME="config.json" ;; esac
  WANT="$(wc -c < "$SRC" | tr -d ' ')"
  OK=0

  # 1) staging file, then copy into the launcher's private folder
  if a push "$SRC" "$TMP/akai-push.tmp" >/dev/null 2>&1; then
    a shell chmod 644 "$TMP/akai-push.tmp" >/dev/null 2>&1
    a shell "run-as $PKG sh -c 'mkdir -p files && cp $TMP/akai-push.tmp \"files/$NAME\"'" >/dev/null 2>&1
    GOT="$(a shell "run-as $PKG stat -c%s \"files/$NAME\"" 2>/dev/null | tr -d '\r')"
    a shell rm -f "$TMP/akai-push.tmp" >/dev/null 2>&1
    if [ "$GOT" = "$WANT" ]; then OK=1; echo "pushed $NAME ($WANT bytes)"; fi
  fi

  # 2) streaming straight into the private folder
  if [ $OK -eq 0 ]; then
    if a exec-in "run-as $PKG sh -c 'mkdir -p files && cat > \"files/$NAME\"'" < "$SRC" 2>/dev/null; then
      GOT="$(a shell "run-as $PKG stat -c%s \"files/$NAME\"" 2>/dev/null | tr -d '\r')"
      if [ "$GOT" = "$WANT" ]; then OK=1; echo "pushed $NAME ($WANT bytes, streamed)"; fi
    fi
  fi

  if [ $OK -eq 1 ]; then
    # remove any older copy in the shared folder so it cannot shadow this one
    a shell rm -f "$EXT/$NAME" >/dev/null 2>&1
  else
    # 3) last resort: the shared folder
    if a push "$SRC" "$EXT/$NAME" >/dev/null 2>&1; then
      GOT="$(a shell stat -c%s "$EXT/$NAME" 2>/dev/null | tr -d '\r')"
      if [ "$GOT" = "$WANT" ]; then
        a shell touch "$EXT/$NAME" >/dev/null 2>&1
        OK=1; echo "pushed $NAME (shared folder)"
      fi
    fi
  fi
  if [ $OK -eq 0 ]; then echo "FAILED to push $NAME (size check did not match)"; FAIL=1; fi
done

# bring the launcher to the front so it reloads right away, then show what it says
a shell am start -n $PKG/.MainActivity >/dev/null 2>&1 || true
sleep 3
echo "--- launcher says:"
a logcat -d -s AkaiLauncher:I 2>/dev/null | grep -i -E "wallpaper|config" | tail -n 4
exit $FAIL
