#!/bin/bash
set -e
echo "=== Clear Book APK (Docker) ==="
docker run --rm -v "$(pwd):/project" -w /project openjdk:17-jdk-slim bash -c "
apt-get update -qq && apt-get install -y -qq wget unzip >/dev/null 2>&1
cd /tmp
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdtools.zip
unzip -q cmdtools.zip -d /opt/android-sdk/cmdline-tools
mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest
yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk --licenses >/dev/null 2>&1
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk 'platform-tools' 'build-tools;34.0.0' 'platforms;android-34' >/dev/null 2>&1
export ANDROID_HOME=/opt/android-sdk
cd /project
chmod +x gradlew
./gradlew assembleDebug
echo '=== Done ==='
ls -lh app/build/outputs/apk/debug/app-debug.apk
"
