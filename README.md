# CameraController

An Android app for controlling camera motion detection via HTTP API with a home screen widget.

## Development Setup

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 17
- Android SDK 24+ (target SDK 36)

### Build Instructions
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Build and run on device/emulator

## Testing

### Test Camera Server
A Python test server (`test_camera_server.py`) is provided to simulate camera API:
```bash
python3 test_camera_server.py
```

## Build and Deployment

### Local Build Commands
```bash
# Clean and build debug APK
./gradlew clean assembleDebug

# Build release APK (unsigned)
./gradlew assembleRelease

# Build and install debug APK on connected device
./gradlew installDebug
```

