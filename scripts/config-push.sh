#!/data/data/com.termux/files/usr/bin/bash
# Sends your settings (and optional wallpaper / font) to the launcher. No rebuild needed.
#   bash scripts/config-push.sh <tv-ip>[:port] app/src/main/assets/config.json [wallpaper.jpg] [font.ttf]
# The file name decides what it is: config.json, wallpaper.jpg, font.ttf.
PKG=com.mylauncher.akaitv
TV="$1"; shift
if [ -z "$TV" ] || [ $# -eq 0 ]; then
  echo "Usage: bash scripts/config-push.sh <tv-ip> <file> [more files]"; exit 1
fi
case "$TV" in *:*) ADDR="$TV" ;; *) ADDR="$TV:5555" ;; esac
a() { adb -s "$ADDR" "$@"; }
adb connect "$ADDR" >/dev/null 2>&1 || true

DEST_EXT="/sdcard/Android/data/$PKG/files"
for SRC in "$@"; do
  NAME="$(basename "$SRC")"
  case "$SRC" in
    *config.json) NAME="config.json" ;;
  esac
  if a push "$SRC" "$DEST_EXT/$NAME" >/dev/null 2>&1; then
    echo "pushed $NAME"
  else
    # fallback: write into the app's private folder (the debug build allows run-as)
    if a shell "run-as $PKG sh -c 'mkdir -p files && cat > files/$NAME'" < "$SRC"; then
      echo "pushed $NAME (private folder)"
    else
      echo "FAILED to push $NAME"
    fi
  fi
done

# bring the launcher back so it reloads right away
a shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
