#!/usr/bin/env bash
# One command, from Termux: push Mantel to its OWN GitHub repo, wait for the cloud build,
# download the APK, and install it on the TV.
#
#   bash tools/deploy.sh 192.168.29.42:5555
#
# It never makes Mantel your Home app and never uninstalls anything. Your current launcher
# keeps working. It saves your current Home app name to ~/mantel-previous-home.txt first.
#
# Run it from a copy in Termux's home folder (for example ~/mantel), not from shared storage.
# Needs: pkg install git gh android-tools     and     gh auth login -s workflow

set -euo pipefail

TV="${1:-}"
NAME="${2:-mantel}"
if [ -z "$TV" ]; then
  echo "Usage: bash tools/deploy.sh 192.168.29.42:5555 [github-repo-name]"
  exit 1
fi

cd "$(dirname "${BASH_SOURCE[0]}")/.."

case "$PWD" in
  */storage/*|/sdcard/*|/storage/*)
    echo "Run this from a copy in Termux's home folder, not shared storage."
    echo "  cd ~ && unzip -o ~/storage/downloads/Mantel-project.zip && cd ~/mantel"
    exit 1;;
esac

command -v git >/dev/null || { echo "Install git first:  pkg install git"; exit 1; }
command -v gh  >/dev/null || { echo "Install gh first:  pkg install gh"; exit 1; }
command -v adb >/dev/null || { echo "Install adb first:  pkg install android-tools"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Log in first:  gh auth login -s workflow"; exit 1; }

OUTDIR="$HOME/mantel-apk"

# ---- 1. commit and push ---------------------------------------------------------------
git config --global --add safe.directory "$PWD" 2>/dev/null || true
[ -d .git ] || git init -q -b main
git add -A
if ! git diff --cached --quiet; then
  git -c user.name="${GIT_AUTHOR_NAME:-Mantel}" \
      -c user.email="${GIT_AUTHOR_EMAIL:-mantel@users.noreply.github.com}" \
      commit -q -m "Mantel build"
fi

if git remote get-url origin >/dev/null 2>&1; then
  git push -q origin main
else
  gh repo create "$NAME" --private --source=. --remote=origin --push
fi
SHA="$(git rev-parse HEAD)"
echo "Pushed to GitHub."

# ---- 2. wait for the build ------------------------------------------------------------
echo "Waiting for the build to start..."
RUN=""
for _ in $(seq 1 30); do
  RUN="$(gh run list --limit 10 --json databaseId,headSha \
        -q ".[] | select(.headSha==\"$SHA\") | .databaseId" 2>/dev/null | head -n 1 || true)"
  [ -n "$RUN" ] && break
  sleep 4
done
if [ -z "$RUN" ]; then
  echo "The build did not start. Open the repo's Actions tab on GitHub to see why."
  exit 1
fi

echo "Building (a few minutes)..."
if ! gh run watch "$RUN" --exit-status >/dev/null 2>&1; then
  echo
  echo "BUILD FAILED. Nothing was installed and your TV is unchanged. Last lines of the log:"
  gh run view "$RUN" --log-failed | tail -n 60
  echo
  echo "Copy the lines above to Claude and it will fix them."
  exit 1
fi
echo "Build OK."

# ---- 3. download -----------------------------------------------------------------------
rm -rf "$OUTDIR"
gh run download "$RUN" -n Mantel-apk-script -D "$OUTDIR"
[ -f "$OUTDIR/Mantel.apk" ] || { echo "Mantel.apk was not in the download."; exit 1; }
echo "Downloaded $OUTDIR/Mantel.apk"

# ---- 4. install on the TV (Home is NOT changed) ---------------------------------------
adb connect "$TV" >/dev/null
if [ ! -f "$HOME/mantel-previous-home.txt" ]; then
  adb -s "$TV" shell cmd package resolve-activity --brief \
      -a android.intent.action.MAIN -c android.intent.category.HOME \
      | tee "$HOME/mantel-previous-home.txt"
  echo "(Saved your current Home app to ~/mantel-previous-home.txt. The last line is your way back.)"
fi

if ! adb -s "$TV" install -r "$OUTDIR/Mantel.apk"; then
  echo
  echo "Install failed. If it says INSTALL_FAILED_UPDATE_INCOMPATIBLE, run:"
  echo "  adb -s $TV uninstall dev.mantel.tv"
  echo "and then run this script again."
  exit 1
fi

adb -s "$TV" shell monkey -p dev.mantel.tv -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 || true
echo
echo "Installed and opened Mantel on the TV. Your Home app has NOT been changed."
