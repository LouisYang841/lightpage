#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ "$(uname -s)-$(uname -m)" != "Linux-x86_64" ]; then
  echo "This build script currently supports Linux x86_64 only; native ARM64 Termux needs a separate toolchain or Linux CI runner." >&2
  exit 1
fi
mkdir -p .tools out/classes out/dex signing
export TMPDIR="$PWD/.tools/tmp"
mkdir -p "$TMPDIR"

# Android Studio / Gradle are not needed. Download only the compiler,
# Android platform stubs and Android APK packaging/signing tools.
fetch() {
  local url="$1" target="$2" sha1="$3"
  if [ ! -f "$target" ]; then curl --fail --location --retry 2 --max-time 240 "$url" -o "$target"; fi
  printf '%s  %s\n' "$sha1" "$target" | sha1sum -c -
}
if [ ! -f .tools/build-tools/android-15/lib/d8.jar ]; then
  fetch https://dl.google.com/android/repository/build-tools_r35_linux.zip .tools/build-tools.zip 2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc
  unzip -q -o .tools/build-tools.zip -d .tools/build-tools
fi
if [ ! -f .tools/platform/android-35/android.jar ]; then
  fetch https://dl.google.com/android/repository/platform-35_r02.zip .tools/platform.zip 0bb560a90a7a2cbd0dd8348224d518b638fe7949
  unzip -q -o .tools/platform.zip -d .tools/platform
fi
if [ ! -f .tools/ecj.jar ]; then
  curl --fail --location --retry 2 --max-time 120 https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.40.0/ecj-3.40.0.jar -o .tools/ecj.jar
fi
printf '%s  %s\n' 05cc22a24e7982970f63a405fc6c820bc80b806f27f3c5a6236fc475f8f7152b .tools/ecj.jar | sha256sum -c -
# The userscripts in ../userscripts are the only copies of those two files.
# They are copied into the APK assets here so the two never drift apart.
rm -rf out/assets
mkdir -p out/assets
cp ../userscripts/xhs-mobile-layout.user.js ../userscripts/xhs-app-back.user.js out/assets/
cp assets/back.js out/assets/

BT="$PWD/.tools/build-tools/android-15"
PLATFORM="$PWD/.tools/platform/android-35/android.jar"
CORE="$PWD/.tools/platform/android-35/core-for-system-modules.jar"
"$BT/aapt2" compile --dir res -o out/resources.zip
"$BT/aapt2" link -I "$PLATFORM" --manifest AndroidManifest.xml -A out/assets -o out/resources.apk out/resources.zip
java -jar .tools/ecj.jar -1.8 -bootclasspath "$PLATFORM:$CORE" -d out/classes -nowarn src/local/lightpage/xhs/MainActivity.java
mapfile -t CLASSES < <(find out/classes -name '*.class')
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$PLATFORM" --min-api 26 --output out/dex "${CLASSES[@]}"
python3 - <<'PY'
from pathlib import Path
import shutil, zipfile
shutil.copyfile('out/resources.apk', 'out/unsigned.apk')
with zipfile.ZipFile('out/unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    for p in Path('out/dex').glob('*.dex'): z.write(p,p.name)
PY
"$BT/zipalign" -f -p 4 out/unsigned.apk out/aligned.apk
# Signing identity. Reuse your own keystore to keep update compatibility:
#   LIGHTPAGE_KEYSTORE=/path/x.jks LIGHTPAGE_KEYSTORE_PASS=... LIGHTPAGE_KEY_ALIAS=...
# Otherwise a throwaway test key is generated. Only that key's builds upgrade each other.
KS="${LIGHTPAGE_KEYSTORE:-signing/lightpage-test.jks}"
KS_PASS="${LIGHTPAGE_KEYSTORE_PASS:-android}"
KS_ALIAS="${LIGHTPAGE_KEY_ALIAS:-lightpage-test}"
mkdir -p "$(dirname "$KS")"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Lightpage Test, O=Local Development, C=CN' -noprompt
  echo "NOTE: generated a throwaway test keystore at $KS; it cannot upgrade installs signed by another key." >&2
fi
java -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" --out out/lightpage-0.2.0-test.apk out/aligned.apk
java -jar "$BT/lib/apksigner.jar" verify --verbose out/lightpage-0.2.0-test.apk
"$BT/aapt2" dump badging out/lightpage-0.2.0-test.apk
