#!/data/data/com.termux/files/usr/bin/bash
# Makes the remote's Settings button open the launcher's quick settings panel (from any app).
# The TV normally throws that button away once the stock launcher is switched off.
#   bash scripts/enable-settings-key.sh <tv-ip>[:port]
# Undo any time: bash scripts/disable-settings-key.sh <tv-ip>[:port]
PKG=com.mylauncher.akaitv
SVC="$PKG/.KeyService"
TV="$1"
if [ -z "$TV" ]; then echo "Usage: bash scripts/enable-settings-key.sh <tv-ip>[:port]"; exit 1; fi
case "$TV" in *:*) ADDR="$TV" ;; *) ADDR="$TV:5555" ;; esac
a() { adb -s "$ADDR" "$@" </dev/null; }
adb connect "$ADDR" >/dev/null 2>&1
if ! a get-state >/dev/null 2>&1; then echo "Can't reach the TV at $ADDR."; exit 1; fi

CUR="$(a shell settings get secure enabled_accessibility_services | tr -d '\r')"
KEEP="$(echo "$CUR" | tr ':' '\n' | grep -v "^$PKG/" | grep -v -x null | grep -v '^$' | paste -sd: -)"
if [ -n "$KEEP" ]; then NEW="$KEEP:$SVC"; else NEW="$SVC"; fi
a shell settings put secure enabled_accessibility_services "$NEW"
a shell settings put secure accessibility_enabled 1
sleep 2
echo "Accessibility services now: $(a shell settings get secure enabled_accessibility_services | tr -d '\r')"
echo "Press the remote's Settings button on any screen. The Akai settings panel should open."
echo "If nothing happens, run: adb -s $ADDR logcat -d -s AkaiLauncher:I | tail"
