#!/bin/bash
set -e
echo "=== Clear Book APK Builder ==="
if ! command -v java &>/dev/null; then
    echo "Error: Java 17+ required"
    echo "  Ubuntu: sudo apt install openjdk-17-jdk-headless"
    echo "  macOS:  brew install openjdk@17"
    exit 1
fi
if [ -z "$ANDROID_HOME" ]; then
    echo "Error: ANDROID_HOME not set"
    echo "  Install Android SDK: https://developer.android.com/studio"
    echo "  export ANDROID_HOME=/path/to/android-sdk"
    exit 1
fi
echo "Building APK..."
chmod +x gradlew 2>/dev/null
./gradlew assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    echo "Success! APK: $APK ($(du -h "$APK" | cut -f1))"
    echo "Install: adb install $APK"
fi
