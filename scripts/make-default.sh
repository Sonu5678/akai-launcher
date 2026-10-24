#!/data/data/com.termux/files/usr/bin/bash
# Makes Akai the real Home screen, with safety checks and automatic rollback.
# Undo any time with:  bash scripts/restore-original.sh <tv-ip>
#   bash scripts/make-default.sh <tv-ip>[:port]
TV="$1"
PKG=com.mylauncher.akaitv
OURS="$PKG/.MainActivity"
WIZ=com.google.android.tungsten.setupwraith
if [ -z "$TV" ]; then echo "Usage: bash scripts/make-default.sh <tv-ip>[:port]"; exit 1; fi
case "$TV" in *:*) ADDR="$TV" ;; *) ADDR="$TV:5555" ;; esac
BK="$HOME/tv-backup"; mkdir -p "$BK"
a() { adb -s "$ADDR" "$@"; }
home() { a shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 | tr -d '\r'; }

adb connect "$ADDR" >/dev/null 2>&1
if ! a get-state >/dev/null 2>&1; then echo "Can't reach the TV at $ADDR."; exit 1; fi
if ! a shell pm path $PKG | grep -q package; then echo "The launcher isn't installed. Run install.sh first."; exit 1; fi

CUR="$(home)"
if [ "$CUR" = "$OURS" ]; then echo "Akai is already your Home screen."; exit 0; fi
case "$CUR" in
  *setupwraith*|"")
    echo "Home currently points at '$CUR', which is not the normal Google TV home."
    echo "Run:  bash scripts/restore-original.sh $ADDR   and then try again."
    exit 1 ;;
esac
STOCK_ACT="$CUR"
STOCK_PKG="${CUR%%/*}"
echo "$STOCK_ACT" > "$BK/stock-home.txt"
echo "Your current Home is $STOCK_ACT (saved, so it can be restored exactly)."

# Safety check: Android deletes any Wi-Fi network saved by the setup wizard when it is switched off.
OWNED="$(a shell "dumpsys wifi | grep -c 'cname=$WIZ'" | tr -d '\r')"
if [ "${OWNED:-0}" -gt 0 ]; then
  echo
  echo "STOP: some saved Wi-Fi networks belong to the setup wizard. Switching it off would delete them"
  echo "and could take the TV offline. These are the networks:"
  a shell dumpsys wifi | grep -nE "ID: [0-9]+ SSID|cname=$WIZ" | cut -c1-90 | grep -B1 "cname=$WIZ" | grep "ID:"
  echo
  echo "Fix: forget each one that is NOT the Wi-Fi you are using now:"
  echo "  adb -s $ADDR shell cmd wifi forget-network <ID>"
  echo "If the one you are using is listed, forget it in the TV's Settings and join it again."
  echo "Then run this script again."
  exit 1
fi

rollback() {
  echo "Rolling back to the original Home screen..."
  a shell pm enable "$STOCK_PKG"
  a shell pm enable "$WIZ"
  a shell cmd package set-home-activity "$STOCK_ACT"
  a shell am force-stop "$WIZ"
  a shell input keyevent KEYCODE_HOME
}
disable() {
  a shell pm disable-user --user 0 "$1" || return 1
  echo "$1" >> "$BK/disabled-by-me.txt"
}

echo "Switching off the setup wizard and the stock launcher (reversible)..."
disable "$WIZ"       || { rollback; exit 1; }
disable "$STOCK_PKG" || { rollback; exit 1; }
a shell cmd package set-home-activity "$OURS" || { rollback; exit 1; }
sleep 2
NOW="$(home)"
if [ "$NOW" != "$OURS" ]; then
  echo "Home resolved to '$NOW' instead of Akai."
  rollback
  exit 1
fi
a shell input keyevent KEYCODE_HOME
echo
echo "Done. The Home button now opens Akai."
echo "Test it: Home button, then the d-pad, then switch the TV fully off and on."
echo "If anything feels wrong:  bash scripts/restore-original.sh $ADDR"
