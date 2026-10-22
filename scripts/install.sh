#!/data/data/com.termux/files/usr/bin/bash
# Installs the launcher on the TV and makes it the home screen.
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
  echo "Couldn't reach the TV. Check: same Wi-Fi, USB/Network debugging on."
  echo "If your TV only has 'Wireless debugging', pair first:"
  echo "  adb pair <ip>:<pairing-port>   (enter the pairing code shown on the TV)"
  echo "  adb connect <ip>:<port shown on the Wireless debugging screen>"
  echo "then run this script again with that ip:port."
  exit 1
fi

BK="$HOME/tv-backup"; mkdir -p "$BK"
echo "Backing up package list and settings to $BK ..."
a shell pm list packages -f   > "$BK/packages.txt"
a shell pm list packages -d   > "$BK/disabled-before.txt"
a shell getprop               > "$BK/getprop.txt"
a shell settings list global  > "$BK/settings-global.txt"
a shell settings list secure  > "$BK/settings-secure.txt"
a shell settings list system  > "$BK/settings-system.txt"

echo "Downloading the APK..."
curl -fL -o "$BK/launcher.apk" "https://github.com/$REPO/releases/download/latest/launcher.apk"

echo "Installing..."
a push "$BK/launcher.apk" /data/local/tmp/launcher.apk
a shell pm install -r -g /data/local/tmp/launcher.apk
a shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
a shell rm /data/local/tmp/launcher.apk || true

echo "First start (creates its config folder)..."
a shell am start -n $PKG/.MainActivity >/dev/null || true
sleep 4

CFG="$HERE/../app/src/main/assets/config.json"
if [ -f "$CFG" ]; then
  bash "$HERE/config-push.sh" "$ADDR" "$CFG" || true
fi

echo "Setting it as the default home..."
a shell cmd package set-home-activity $PKG/.MainActivity || true

echo
echo "Apps that claim HOME on this TV:"
a shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep -i "packageName" | sort -u || true

a shell input keyevent KEYCODE_HOME || true
echo
echo "Done. Press Home on the remote."
echo "If a different home screen still appears, see 'Troubleshooting' in the README."
echo "When you're finished, switch USB/Network debugging OFF on the TV."
