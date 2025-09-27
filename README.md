# CameraController

An Android app for controlling camera motion detection via HTTP API with a home screen widget.

## Core Functionality
• **Motion Detection Control**: Toggle camera motion detection via HTTP API
• **Home Screen Widget**: Quick access widget for motion detection toggle
• **WiFi-Aware Updates**: Automatic widget updates when connected to target WiFi
• **Digest Authentication**: Secure camera API communication

## Widget Triggers
• **Manual Widget Tap**: Smart button behavior - toggles when status known, refreshes when unknown
• **Periodic Updates**: Every 5 minutes when on target WiFi network (WiFiAwareWidgetWorker)
• **Network Changes**: Updates when WiFi connectivity changes (ConnectivityChangeReceiver)

## API Endpoints
• **Get Status**: `GET /cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect`
• **Set Status**: `GET /cgi-bin/configManager.cgi?action=setConfig&MotionDetect[].Enable={true|false}`
• **Authentication**: Digest auth with username:password@ip:port format
• **Timeout**: Configurable request timeout (default: 2 seconds)

## Configuration Options
• **Camera Endpoint**: IP address and port
• **Credentials**: Username and password
• **API Paths**: Custom get/set configuration paths
• **Timeout**: HTTP request timeout in seconds
• **WiFi Network**: Target WiFi network name for automatic updates

## Permissions
• **Internet**: HTTP API communication
• **Network State**: Monitor connectivity changes
• **WiFi State**: Check current WiFi network
• **Boot Complete**: Initialize widget after restart
• **Foreground Service**: Background widget updates
• **Wake Lock**: Prevent device sleep during operations
• **Notifications**: Widget update notifications

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

### Dependencies
- **UI**: Jetpack Compose, Material3
- **Networking**: Retrofit, OkHttp with digest auth
- **Data**: DataStore Preferences
- **Background**: WorkManager
- **Widgets**: AppWidget framework

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

