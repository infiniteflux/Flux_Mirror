# 📱 Flux_Mirror - Advanced Android Screen Mirroring & Casting Solution

## 🌟 Project Overview

**Flux_Mirror** is a sophisticated Android application that enables seamless screen mirroring and casting capabilities across multiple protocols and devices. Built with modern Android architecture using Jetpack Compose and Kotlin, this application provides three distinct casting modes: **Local Device Casting (Miracast/WiFi Direct)**, **Web Casting**, and **Floating Tools** for enhanced productivity.

---

## 🎯 Key Features

### 1. **Local Cast (Miracast/WiFi Direct)**
- **Miracast Device Discovery**: Automatic discovery of Miracast-enabled displays (TVs, monitors, projectors)
- **Network Device Discovery**: Detection of Chromecast, Apple TV, and AirPlay devices using mDNS/NSD
- **WiFi Display Support**: Full integration with Android's WiFi Display framework
- **Multi-Protocol Support**: Handles both WiFi Direct (P2P) and network-based casting
- **Real-time Device Scanning**: Continuous scanning with multicast lock for optimal discovery
- **Connection Management**: Automated connection flow with status monitoring

### 2. **Web Cast**
- **HTTP Server Streaming**: Embedded Ktor server for browser-based viewing
- **WebSocket Real-time Streaming**: Low-latency screen capture via WebSocket
- **Cross-Platform Viewing**: Access screen from any device with a web browser
- **Network Discovery**: Automatic IP address detection and sharing
- **Media Projection**: Utilizes Android's MediaProjection API for high-quality capture
- **Interactive Web UI**: Responsive HTML5 viewer with fullscreen and screenshot features

### 3. **Floating Tools**
- **Overlay Windows**: System-level floating windows using `SYSTEM_ALERT_WINDOW`
- **Picture-in-Picture**: Persistent floating interface over other apps
- **Service-Based Architecture**: Lifecycle-aware service for reliable operation
- **Compose Integration**: Jetpack Compose UI in floating window context

---

## 🏗️ Technical Architecture

### **Technology Stack**

#### **Core Framework**
- **Language**: Kotlin 2.0.21
- **Build System**: Gradle 8.13.1 with Kotlin DSL
- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 36)
- **Compile SDK**: Android 14 (API 36)

#### **UI Framework**
- **Jetpack Compose**: Modern declarative UI framework
  - Compose BOM: 2024.09.00
  - Material 3: 1.4.0
  - Animation Core: 1.9.4
  - Activity Compose: 1.11.0

#### **Backend & Networking**
- **Ktor Server**: 2.3.12
  - `ktor-server-core-jvm`: Core server functionality
  - `ktor-server-netty-jvm`: Netty-based server engine
  - `ktor-server-websockets-jvm`: WebSocket support
  - `ktor-server-content-negotiation-jvm`: Content type handling

- **Ktor Client**: 2.3.12
  - `ktor-client-core`: HTTP client foundation
  - `ktor-client-android`: Android-optimized HTTP engine
  - `ktor-client-websockets`: WebSocket client support

#### **Device Discovery**
- **mDNS/NSD**: `javax.jmdns:jmdns:3.4.1` for service discovery
- **WiFi P2P**: Android's native WiFi Direct framework
- **MediaRouter**: Android's MediaRouter for display routing

#### **Media Capture**
- **MediaProjection API**: Screen capture from Android 5.0+
- **VirtualDisplay**: Creates virtual display for mirroring
- **ImageReader**: Hardware-accelerated image capture
- **CameraX**: 1.3.1 for camera preview features

#### **Concurrency**
- **Kotlin Coroutines**: 1.7.3
  - `kotlinx-coroutines-android`: Android-specific implementations
  - `kotlinx-coroutines-core`: Core coroutine library

#### **Serialization**
- **Kotlinx Serialization JSON**: 1.6.2 for data handling

#### **Logging**
- **SLF4J Android**: 1.7.36 for comprehensive logging

#### **Android Jetpack**
- **Core KTX**: 1.17.0
- **Lifecycle Runtime KTX**: 2.9.4
- **ViewModel Compose**: 2.8.3
- **SavedState**: 1.2.1
- **MediaRouter**: 1.6.0

---

## 📦 Project Structure

```
Flux_Mirror/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/flux_mirror/
│   │   │   │   ├── MainActivity.kt                    # Main entry point with navigation
│   │   │   │   ├── network/
│   │   │   │   │   ├── DeviceDiscoveryManager.kt     # Multi-protocol device discovery
│   │   │   │   │   └── MiracastConnectionManager.kt  # Miracast connection handling
│   │   │   │   ├── service/
│   │   │   │   │   ├── ScreenMirroringService.kt     # Web/Local casting service
│   │   │   │   │   ├── MiracastService.kt            # Miracast-specific service
│   │   │   │   │   ├── FloatingToolsService.kt       # Overlay window service
│   │   │   │   │   ├── DrawingOverlayService.kt      # Drawing overlay features
│   │   │   │   │   └── CameraPreviewService.kt       # Camera preview overlay
│   │   │   │   ├── screen/
│   │   │   │   │   ├── WebcastingScreen.kt           # Web cast UI
│   │   │   │   │   ├── SimpleTestScreen.kt           # Floating tools UI
│   │   │   │   │   └── FloatingScreen.kt             # Floating window content
│   │   │   │   ├── viewmodel/
│   │   │   │   │   └── DevicesViewModel.kt           # Device state management
│   │   │   │   ├── permission/
│   │   │   │   │   └── PermissionsHelper.kt          # Comprehensive permission management
│   │   │   │   └── ui/theme/
│   │   │   │       ├── Color.kt                       # Theme colors
│   │   │   │       ├── Theme.kt                       # Application theme
│   │   │   │       ├── Type.kt                        # Typography
│   │   │   │       └── ScreenMirroringTheme.kt       # Complete theme definition
│   │   │   ├── res/
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   └── ...
│   │   │   └── AndroidManifest.xml                    # App configuration and permissions
│   │   ├── androidTest/                               # Instrumented tests
│   │   └── test/                                      # Unit tests
│   ├── build.gradle.kts                               # Module-level build configuration
│   └── proguard-rules.pro                             # ProGuard configuration
├── gradle/
│   └── libs.versions.toml                             # Centralized dependency versions
├── build.gradle.kts                                   # Project-level build configuration
├── settings.gradle.kts                                # Gradle settings
├── gradle.properties                                  # Gradle properties
└── README.md                                          # This file
```


## 🚀 How to Build & Run

### **Prerequisites**
- Android Studio Hedgehog | 2023.1.1 or later
- JDK 11 or higher
- Android SDK with API 36 installed
- Physical Android device (API 24+) or emulator

### **Build Steps**

1. **Clone Repository**
   ```bash
   git clone <https://github.com/infiniteflux/Flux_Mirror>
   cd Flux_Mirror
   ```

2. **Open in Android Studio**
   - File → Open → Select `Flux_Mirror` directory

3. **Sync Gradle**
   - Android Studio will automatically sync Gradle
   - If not, click "Sync Project with Gradle Files"

4. **Build APK**
   ```bash
   ./gradlew assembleDebug
   ```
   APK location: `app/build/outputs/apk/debug/app-debug.apk`

5. **Install & Run**
   ```bash
   ./gradlew installDebug
   adb shell am start -n com.flux_mirror/.MainActivity
   ```

### **Configuration**

#### **Local Properties**
Create `local.properties` (usually auto-generated):
```properties
sdk.dir=C\:\\Users\\<username>\\AppData\\Local\\Android\\Sdk
```

#### **Gradle Properties**
`gradle.properties` contains:
```properties
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

---

## 📱 Usage Guide

### **Tab 1: Local Cast (Miracast/Network Devices)**

1. **Grant Permissions**
   - On first launch, tap "Grant Permission"
   - Allow all requested permissions

2. **Device Discovery**
   - App automatically scans for:
     - Miracast/WiFi Display devices
     - Chromecasts
     - Apple TVs

3. **Connect to Device**
   - Tap "Cast to Device"
   - Select device from list
   - For Miracast: Cast Screen settings will open → Connect manually
   - For Network: Casting starts automatically

4. **Stop Casting**
   - Tap "Stop Casting" button
   - Or pull down notification and tap "Stop"

### **Tab 2: Web Cast**

1. **Start Web Server**
   - Tap "Start Web Cast"
   - Grant screen capture permission

2. **View on Browser**
   - Note displayed URL (e.g., `http://192.168.1.100:8080`)
   - Open URL in any browser on same WiFi network
   - Desktop, laptop, tablet, or another phone

3. **Web Viewer Features**
   - Click "Fullscreen" for immersive view
   - Click "Screenshot" to save current frame
   - See live FPS counter

4. **Stop Casting**
   - Tap "Stop Web Cast" in app
   - Or stop from notification

### **Tab 3: Floating Tools**

1. **Grant Overlay Permission**
   - Tap "Start Floating"
   - Allow "Display over other apps" permission

2. **Floating Window**
   - Floating window appears on screen
   - Drag to reposition
   - Works over all apps

3. **Stop Floating**
   - Return to app and tap "Stop Floating"
   - Or swipe away notification

---

## 🧪 Testing

### **Unit Tests**
Located in: `app/src/test/java/com/flux_mirror/`
```bash
./gradlew test
```

### **Instrumented Tests**
Located in: `app/src/androidTest/java/com/flux_mirror/`
```bash
./gradlew connectedAndroidTest
```

### **Manual Testing Checklist**

#### **Local Cast**
- [ ] Miracast device appears in list
- [ ] Cast Screen opens on device selection
- [ ] Connection status updates correctly
- [ ] Stop button terminates casting
- [ ] Notification appears and works

#### **Web Cast**
- [ ] IP address displays correctly
- [ ] Browser loads web viewer
- [ ] Screen updates in real-time
- [ ] Fullscreen mode works
- [ ] Screenshot downloads correctly

#### **Floating Tools**
- [ ] Overlay permission request appears
- [ ] Floating window displays
- [ ] Window is draggable
- [ ] Window persists over other apps
- [ ] Stop removes floating window

---

## 🐛 Known Limitations

1. **Miracast Connection**: Cannot programmatically connect to Miracast devices (Android security restriction). User must manually connect via Cast Screen settings.

2. **Network Restrictions**: Web Cast requires devices to be on same WiFi network. Does not work over mobile data or different networks.

3. **Performance**: Web Cast FPS depends on:
   - Device CPU performance
   - Network bandwidth
   - Number of connected clients

4. **API Level**: Some features require newer Android versions:
   - Miracast: API 21+
   - Overlay permission: API 23+
   - Nearby WiFi Devices: API 31+

5. **Battery Usage**: Screen capture and server streaming consume significant battery. Recommend using while charging for extended sessions.

---

## 🔬 Advanced Technical Details

### **Screen Capture Pipeline**

```
MediaProjection → VirtualDisplay → ImageReader → Bitmap → JPEG → WebSocket → Browser
                                 ↓
                         ImageReader Thread (Handler)
                                 ↓
                         ServiceScope (IO Dispatcher)
```

### **Device Discovery Flow**

```
User Opens App → DeviceDiscoveryManager.discoverDevices()
                 ↓
        ┌────────┴──────────┬──────────────┬─────────────┐
        ↓                   ↓              ↓             ↓
    NSD Discovery    MediaRouter    WiFi P2P    DisplayManager
   (_googlecast,     (LIVE_VIDEO   discoverPeers  scanWifiDisplays
    _airplay, etc)    routes)                    getWifiDisplayStatus
        ↓                   ↓              ↓             ↓
        └────────┬──────────┴──────────────┴─────────────┘
                 ↓
         Flow<DiscoveredDevice>
                 ↓
         DevicesViewModel
                 ↓
     StateFlow<List<Device>>
                 ↓
         LocalCastScreen UI
```

### **Ktor Server Architecture**

```kotlin
embeddedServer(Netty, port = 8080) {
    install(WebSockets)
    routing {
        get("/") { /* HTML Viewer */ }
        webSocket("/stream") {
            while (true) {
                val frame = getCurrentFrame()
                send(Frame.Binary(frame))
            }
        }
    }
}
```
## 🔧 Troubleshooting

### **Issue: No devices found in Local Cast**

**Solutions**:
1. Ensure WiFi is enabled
2. Check all permissions are granted (Settings → Apps → Flux_Mirror → Permissions)
3. Ensure Miracast device is powered on and in pairing mode
4. Try restarting WiFi on phone
5. Check Logcat for discovery errors: `adb logcat | grep DeviceDiscovery`

### **Issue: Web Cast URL not accessible**

**Solutions**:
1. Verify both devices are on same WiFi network
2. Check firewall on viewing device
3. Try direct IP: `http://<phone-ip>:8080`
4. Restart Web Cast
5. Check Logcat for Ktor errors: `adb logcat | grep ScreenMirrorService`

### **Issue: Floating window not appearing**

**Solutions**:
1. Go to Settings → Apps → Special access → Display over other apps
2. Enable for Flux_Mirror
3. Restart app
4. Check Logcat: `adb logcat | grep FloatingToolsService`

### **Issue: Miracast connection timeout**

**Solutions**:
1. Manually connect to Miracast device in Cast Screen settings within 60 seconds
2. Ensure Miracast device is not connected to another device
3. Try forgetting and re-pairing Miracast device
4. Check WiFi Direct is not disabled in developer options

---

## 📄 License

[Add your license information here]

---

## 👨‍💻 Developer

**Project**: Flux_Mirror  
**Package**: `com.flux_mirror`  
**Namespace**: `com.flux_mirror`

---

## 🙏 Acknowledgments

- **Android Jetpack**: For modern Android development tools
- **Ktor**: For lightweight HTTP server framework
- **Kotlin Coroutines**: For elegant asynchronous programming
- **Material Design 3**: For beautiful UI components

---

## 📞 Support

For issues, questions, or contributions, please refer to the project's issue tracker.

---

**Last Updated**: 2025-12-01  
**Version**: 1.0  
**Minimum Android**: 7.0 (API 24)  
**Target Android**: 14 (API 36)