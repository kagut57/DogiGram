#!/bin/bash
set -e

echo "=================================="
echo "Building Telegram Fork APK (No Groups/Channels)"
echo "=================================="
echo ""

# Make gradlew executable
chmod +x ./gradlew

echo "Starting Gradle build..."
echo "This may take 15-30 minutes depending on your system."
echo ""

# Build the APK in release mode
./gradlew :TMessagesProj_App:assembleRelease

echo ""
echo "=================================="
echo "Build completed successfully!"
echo "=================================="
echo ""
echo "APK location:"
ls -lh TMessagesProj_App/build/outputs/apk/release/*.apk

echo ""
echo "To install the APK on your device:"
echo "adb install TMessagesProj_App/build/outputs/apk/release/TMessagesProj_App-release.apk"
