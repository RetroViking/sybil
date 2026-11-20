#!/bin/sh
# Builds ../capture.apk, SIByL's Capture for a phone, with no Gradle: Android's build tools (aapt2, d8, zipalign,
# apksigner) and a platform's android.jar from $ANDROID_HOME (build-tools/*/ and platforms/android-35/: Google's two
# zips, unpacked), and a JDK. Its key, made on the first build, stays in ~/.config/sybil-capture.jks: an update must be
# signed with the key the installed one was, so keep it (and back it up).
set -e
cd "$(dirname "$0")"
sdk=${ANDROID_HOME:-${XDG_DATA_HOME:-$HOME/.local/share}/android-sdk}
tools=$(ls -d "$sdk"/build-tools/* | tail -n 1)
jar=$sdk/platforms/android-35/android.jar
key=${XDG_CONFIG_HOME:-$HOME/.config}/sybil-capture.jks
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT

[ -e "$key" ] || (umask 077; keytool -genkeypair -keystore "$key" -storepass sybil-capture -alias capture -keyalg RSA \
	-keysize 2048 -validity 36500 -dname CN=SIByL)
"$tools/aapt2" compile --dir res -o "$out/res.zip"
"$tools/aapt2" link -I "$jar" --manifest AndroidManifest.xml -o "$out/app.apk" "$out/res.zip"
javac -encoding UTF-8 --release 11 -classpath "$jar" -d "$out/classes" -Xlint:-options src/sybil/capture/*.java
"$tools/d8" --release --min-api 29 --lib "$jar" --output "$out" $(find "$out/classes" -name '*.class')
(cd "$out" && zip -q app.apk classes.dex)
"$tools/zipalign" -p -f 4 "$out/app.apk" "$out/aligned.apk"
"$tools/apksigner" sign --ks "$key" --ks-pass pass:sybil-capture --out ../capture.apk "$out/aligned.apk"
rm -f ../capture.apk.idsig
