#!/usr/bin/env bash
# Builds Mantel.apk with the plain Android SDK command-line tools (no Gradle, no downloads).
#
# Needs: a JDK (17 or newer), and an Android SDK with build-tools and a platform (android.jar).
# Finds the SDK from ANDROID_HOME or ANDROID_SDK_ROOT. Run from the project folder:
#     bash tools/build.sh
# Result: build/Mantel.apk  (signed with a local debug key, so it can be sideloaded)
#
# Nothing here touches a TV or a phone; it only reads the project and writes ./build.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/app/src/main"
OUT="$ROOT/build"
PKG="dev.mantel.tv"
VERSION_NAME="0.1.0"
VERSION_CODE="1"
MIN_SDK="26"
TARGET_SDK="34"

say() { printf '\n== %s\n' "$*"; }
die() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

# ---- find the SDK pieces -------------------------------------------------------------------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] && [ -d "$SDK" ] || die "Set ANDROID_HOME to your Android SDK folder (it contains build-tools and platforms)."

# newest build-tools folder
BT=""
for d in "$SDK"/build-tools/*/; do
  [ -x "${d}aapt2" ] && BT="${d%/}"   # later (higher) names win in glob order
done
[ -n "$BT" ] || die "No build-tools with aapt2 found under $SDK/build-tools."

# a platform android.jar, API 34 preferred, otherwise the newest one at least API 26
JAR=""
if [ -f "$SDK/platforms/android-$TARGET_SDK/android.jar" ]; then
  JAR="$SDK/platforms/android-$TARGET_SDK/android.jar"
else
  for d in "$SDK"/platforms/android-*/; do
    [ -f "${d}android.jar" ] && JAR="${d}android.jar"
  done
fi
[ -n "$JAR" ] || die "No platforms/android-*/android.jar found under $SDK."

command -v javac >/dev/null || die "javac not found. Install a JDK (17 or newer)."
command -v keytool >/dev/null || die "keytool not found (it comes with the JDK)."
command -v zip >/dev/null || die "zip not found. Install it (Termux: pkg install zip)."

AAPT2="$BT/aapt2"
D8="$BT/d8"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"
[ -x "$D8" ] || die "d8 not found in $BT."
[ -x "$APKSIGNER" ] || die "apksigner not found in $BT."

say "Using build-tools: $BT"
say "Using platform:    $JAR"

# ---- clean ---------------------------------------------------------------------------------
rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/classes" "$OUT/dex" "$OUT/manifest"

# ---- manifest: aapt2 wants the package name written in it ----------------------------------
sed "s|<manifest |<manifest package=\"$PKG\" |" "$SRC/AndroidManifest.xml" > "$OUT/manifest/AndroidManifest.xml"

# ---- resources and assets -----------------------------------------------------------------
say "Compiling resources"
"$AAPT2" compile --dir "$SRC/res" -o "$OUT/res/"

say "Linking resources, manifest and fonts"
"$AAPT2" link \
  -o "$OUT/base.apk" \
  -I "$JAR" \
  --manifest "$OUT/manifest/AndroidManifest.xml" \
  -A "$SRC/assets" \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --no-auto-version \
  "$OUT"/res/*.flat

# ---- java -> classes -> dex ---------------------------------------------------------------
say "Compiling Java"
find "$SRC/java" -name '*.java' > "$OUT/sources.txt"
javac --release 11 -Xlint:-options -encoding UTF-8 \
  -classpath "$JAR" -d "$OUT/classes" @"$OUT/sources.txt"

say "Making classes.dex"
"$D8" --release --min-api "$MIN_SDK" --lib "$JAR" --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

say "Adding classes.dex to the APK"
( cd "$OUT/dex" && zip -q -u "$OUT/base.apk" classes.dex )

# ---- align and sign -----------------------------------------------------------------------
KEYSTORE="${MANTEL_KEYSTORE:-$HOME/.mantel-debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  say "Creating a local signing key at $KEYSTORE (keep it: later builds must use the same key)"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias mantel -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Mantel Debug,O=Mantel,C=IN" >/dev/null 2>&1
fi

say "Aligning and signing"
if [ -x "$ZIPALIGN" ]; then
  "$ZIPALIGN" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"
else
  cp "$OUT/base.apk" "$OUT/aligned.apk"
fi
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias mantel --out "$OUT/Mantel.apk" "$OUT/aligned.apk"
"$APKSIGNER" verify "$OUT/Mantel.apk" >/dev/null || die "The signed APK did not verify."

say "Done: $OUT/Mantel.apk"
ls -l "$OUT/Mantel.apk"
