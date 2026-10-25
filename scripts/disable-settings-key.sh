#!/data/data/com.termux/files/usr/bin/bash
# Switches the Settings-button helper off again (leaves any other accessibility service alone).
#   bash scripts/disable-settings-key.sh <tv-ip>[:port]
PKG=com.mylauncher.akaitv
TV="$1"
if [ -z "$TV" ]; then echo "Usage: bash scripts/disable-settings-key.sh <tv-ip>[:port]"; exit 1; fi
case "$TV" in *:*) ADDR="$TV" ;; *) ADDR="$TV:5555" ;; esac
a() { adb -s "$ADDR" "$@" </dev/null; }
adb connect "$ADDR" >/dev/null 2>&1
if ! a get-state >/dev/null 2>&1; then echo "Can't reach the TV at $ADDR."; exit 1; fi
CUR="$(a shell settings get secure enabled_accessibility_services | tr -d '\r')"
KEEP="$(echo "$CUR" | tr ':' '\n' | grep -v "^$PKG/" | grep -v -x null | grep -v '^$' | paste -sd: -)"
if [ -n "$KEEP" ]; then
  a shell settings put secure enabled_accessibility_services "$KEEP"
else
  a shell settings delete secure enabled_accessibility_services
  a shell settings put secure accessibility_enabled 0
fi
echo "Accessibility services now: $(a shell settings get secure enabled_accessibility_services | tr -d '\r')"
