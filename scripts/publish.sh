#!/data/data/com.termux/files/usr/bin/bash
# Run this inside the unzipped project folder, in Termux.
# Creates a public GitHub repo, pushes the project, and waits for the APK build.
#   bash scripts/publish.sh [repo-name]
set -e
NAME="${1:-akai-launcher}"

pkg install -y git gh >/dev/null 2>&1 || true

if ! gh auth status >/dev/null 2>&1; then
  echo "Log in to GitHub (choose GitHub.com, HTTPS, 'Login with a web browser')."
  gh auth login
fi

git config user.name  >/dev/null 2>&1 || git config user.name  "launcher"
git config user.email >/dev/null 2>&1 || git config user.email "launcher@users.noreply.github.com"

[ -d .git ] || git init -b main
git add -A
git commit -m "Akai TV launcher" >/dev/null 2>&1 || true

gh repo create "$NAME" --public --source=. --remote=origin --push

echo "Pushed. Waiting for the build (about 3 minutes)..."
sleep 8
gh run watch --exit-status || true

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
echo
echo "Done. Your repo: $REPO"
echo "Next: bash scripts/install.sh $REPO <TV-IP>"
