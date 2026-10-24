#!/data/data/com.termux/files/usr/bin/bash
# Installs (or updates) the launcher on the TV. It does NOT touch your Home screen:
# use scripts/make-default.sh for that, and scripts/restore-original.sh to undo it.
#   bash scripts/install.sh <github-user/repo> <tv-ip>[:port]
set -e
REPO="$1"; TV="$2"
PKG=com.mylauncher.akaitv
HERE="$(cd "$(dirname "$0")" && pwd)"

if [ -z "$REPO" ] || [ -z "$TV" ]; then
  echo "Usage: bash scripts/install.sh <github-user/repo> <tv-ip>"; exit 1
fi
case "$TV" in *:*) ADDR="$TV" ;; *) ADDR="$TV:5555" ;; esac

command -v adb  >/dev/null || pkg install -y android-tools
command -v curl >/dev/null || pkg install -y curl

a() { adb -s "$ADDR" "$@"; }

echo "Connecting to $ADDR (press Allow on the TV if it asks)..."
adb connect "$ADDR" || true
sleep 2
if ! adb -s "$ADDR" get-state >/dev/null 2>&1; then
  echo
  echo "Couldn't reach the TV. Check: same Wi-Fi, Wireless/USB debugging on."
  echo "If your TV only has 'Wireless debugging', pair first:"
  echo "  adb pair <ip>:<pairing-port>   (enter the pairing code shown on the TV)"
  echo "  adb connect <ip>:<port shown on the Wireless debugging screen>"
  echo "then run this script again with that ip:port."
  exit 1
fi

BK="$HOME/tv-backup"; mkdir -p "$BK"
if [ ! -f "$BK/packages.txt" ]; then
  echo "Saving a backup of your package list and settings to $BK ..."
  a shell pm list packages -f   > "$BK/packages.txt"
  a shell pm list packages -d   > "$BK/disabled-before.txt"
  a shell getprop               > "$BK/getprop.txt"
  a shell settings list global  > "$BK/settings-global.txt"
  a shell settings list secure  > "$BK/settings-secure.txt"
  a shell settings list system  > "$BK/settings-system.txt"
fi

echo "Downloading the APK..."
rm -f "$BK/launcher.apk"
if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  gh release download latest --repo "$REPO" --pattern launcher.apk --dir "$BK" --clobber
else
  curl -fL -o "$BK/launcher.apk" "https://github.com/$REPO/releases/download/latest/launcher.apk"
fi
if [ ! -s "$BK/launcher.apk" ]; then echo "Download failed."; exit 1; fi

echo "Installing..."
a push "$BK/launcher.apk" /data/local/tmp/launcher.apk
a shell pm install -r -g /data/local/tmp/launcher.apk
a shell rm /data/local/tmp/launcher.apk || true
# lets the launcher read your apps' "continue watching" lists, if this TV allows it
a shell pm grant $PKG android.permission.READ_TV_LISTINGS >/dev/null 2>&1 \
  && echo "Watch-next access granted." \
  || echo "(This TV doesn't allow Watch-next access; My Shows will use the shows you pick by hand.)"

echo "First start (creates its config folder)..."
a shell am start -n $PKG/.MainActivity >/dev/null || true
sleep 4

CFG="$HERE/../app/src/main/assets/config.json"
if [ -f "$CFG" ]; then
  bash "$HERE/config-push.sh" "$ADDR" "$CFG" || true
fi

NOW="$(a shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 | tr -d '\r')"
echo
if [ "$NOW" = "$PKG/.MainActivity" ]; then
  echo "Done. Akai is already your Home screen. Press Home on the remote."
else
  echo "Done. Open it with your shortcut button."
  echo "To make the Home button open it too:  bash scripts/make-default.sh $ADDR"
fi
echo "When you're finished, switch USB/Wireless debugging OFF on the TV."
