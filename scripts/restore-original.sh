#!/data/data/com.termux/files/usr/bin/bash
# Puts the TV back to exactly how it was: the Google TV Home screen, everything we switched off
# switched back on. Your apps and accounts are never touched.
#   bash scripts/restore-original.sh <tv-ip>[:port]            go back to the stock home
#   bash scripts/restore-original.sh <tv-ip>[:port] --remove   ...and uninstall Akai completely
#   bash scripts/restore-original.sh <tv-ip>[:port] --dns-off  ...and turn Private DNS back to automatic
PKG=com.mylauncher.akaitv
WIZ=com.google.android.tungsten.setupwraith
TV="$1"; shift
if [ -z "$TV" ]; then echo "Usage: bash scripts/restore-original.sh <tv-ip>[:port] [--remove] [--dns-off]"; exit 1; fi
case "$TV" in *:*) ADDR="$TV" ;; *) ADDR="$TV:5555" ;; esac
BK="$HOME/tv-backup"
a() { adb -s "$ADDR" "$@"; }

adb connect "$ADDR" >/dev/null 2>&1
if ! a get-state >/dev/null 2>&1; then echo "Can't reach the TV at $ADDR."; exit 1; fi

STOCK_ACT="com.google.android.apps.tv.launcherx/.home.HomeActivity"
[ -s "$BK/stock-home.txt" ] && STOCK_ACT="$(cat "$BK/stock-home.txt")"
STOCK_PKG="${STOCK_ACT%%/*}"

echo "Switching the Settings-button helper off..."
bash "$(dirname "$0")/disable-settings-key.sh" "$ADDR" >/dev/null 2>&1 || true

echo "Re-enabling what was switched off..."
if [ -f "$BK/disabled-by-me.txt" ]; then
  sort -u "$BK/disabled-by-me.txt" | while read -r P; do
    [ -n "$P" ] && a shell pm enable "$P"
  done
fi
a shell pm enable "$STOCK_PKG"
a shell pm enable "$WIZ"

echo "Pointing Home back at $STOCK_ACT ..."
a shell cmd package set-home-activity "$STOCK_ACT"
a shell am force-stop "$WIZ" >/dev/null 2>&1

for ARG in "$@"; do
  case "$ARG" in
    --remove) echo "Uninstalling Akai..."; a uninstall $PKG ;;
    --dns-off) a shell settings put global private_dns_mode opportunistic; echo "Private DNS set back to automatic." ;;
  esac
done

NOW="$(a shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 | tr -d '\r')"
echo "Home now resolves to: $NOW"
a shell input keyevent KEYCODE_HOME
echo "Done. Press Home on the remote."
echo "(Akai is still installed unless you used --remove. Your shortcut button can still open it.)"
