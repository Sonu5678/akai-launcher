#!/data/data/com.termux/files/usr/bin/bash
# One command to apply a new version: unzips it over your project, pushes it to GitHub,
# waits for the build, and installs it on the TV.
#   bash scripts/update.sh <tv-ip>[:port] [path-to-zip]
# With no zip given it uses the newest akai-launcher*.zip in your Downloads.
set -e
TV="$1"; ZIP="$2"
if [ -z "$TV" ]; then echo "Usage: bash scripts/update.sh <tv-ip>[:port] [zip]"; exit 1; fi
cd "$(dirname "$0")/.."

if [ -z "$ZIP" ]; then
  ZIP="$(ls -t "$HOME"/storage/downloads/akai-launcher*.zip 2>/dev/null | head -n 1)"
fi
if [ ! -f "$ZIP" ]; then echo "Couldn't find the zip. Pass its path as the second argument."; exit 1; fi
echo "Using $ZIP"

TMP="$(mktemp -d)"
unzip -q "$ZIP" -d "$TMP"
SRC="$(find "$TMP" -maxdepth 1 -mindepth 1 -type d | head -n 1)"
if [ ! -f "$SRC/app/build.gradle" ]; then echo "That zip doesn't look like the launcher project."; exit 1; fi

CFG=app/src/main/assets/config.json
ver() { grep -o '"configVersion"[[:space:]]*:[[:space:]]*[0-9]*' "$1" 2>/dev/null | grep -o '[0-9]*$' | head -n 1; }
OLDV="$(ver "$CFG")"; NEWV="$(ver "$SRC/$CFG")"
OLDV="${OLDV:-0}"; NEWV="${NEWV:-0}"

KEEP=""
if [ -f "$CFG" ] && [ "$NEWV" -le "$OLDV" ]; then
  KEEP="$TMP/keep-config.json"
  cp "$CFG" "$KEEP"
elif [ -f "$CFG" ]; then
  mkdir -p "$HOME/tv-backup"
  SAVED="$HOME/tv-backup/config-before-update-$(date +%Y%m%d-%H%M%S).json"
  cp "$CFG" "$SAVED"
  echo "New settings format: your old config.json was saved to $SAVED"
fi

cp -r "$SRC"/. .
if [ -n "$KEEP" ]; then cp "$KEEP" "$CFG"; echo "Kept your existing config.json."; fi
rm -rf "$TMP"

git add -A
if git diff --cached --quiet; then
  echo "No code changes to push."
else
  git commit -q -m "Update launcher"
  git push -q
  echo "Pushed to GitHub."
fi

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
SHA="$(git rev-parse HEAD)"
RUN=""
echo "Waiting for the build to start..."
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  RUN="$(gh run list --commit "$SHA" --limit 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)"
  if [ -n "$RUN" ]; then break; fi
  sleep 5
done
if [ -z "$RUN" ]; then echo "GitHub didn't start a build. Open the repo's Actions tab and check."; exit 1; fi

echo "Building (about 2 minutes)..."
if ! gh run watch "$RUN" --exit-status >/dev/null 2>&1; then
  echo
  echo "BUILD FAILED. Nothing was installed and your TV is unchanged. Last lines of the log:"
  gh run view "$RUN" --log-failed | tail -n 60
  echo
  echo "Copy the lines above to Claude and it will fix them."
  exit 1
fi
echo "Build OK."

bash scripts/install.sh "$REPO" "$TV"
