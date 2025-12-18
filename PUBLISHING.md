# 🏗️ Building & Publishing Battery Drainer

This guide walks you through building the APK and publishing to Google Play Store.

---

## 📋 Prerequisites

### 1. Install Android Studio
Download from: https://developer.android.com/studio

### 2. Install Java JDK 17+
```bash
# Ubuntu/Debian
sudo apt install openjdk-17-jdk

# macOS (with Homebrew)
brew install openjdk@17

# Windows: Download from https://adoptium.net/
```

---

## 🔨 Building the APK

### Option A: Using Android Studio (Recommended)

1. **Open Android Studio**
2. **File → Open** → Select the `BatteryDrainer` folder
3. Wait for Gradle sync to complete
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Option B: Command Line

```bash
# Navigate to project
cd BatteryDrainer

# Create local.properties with your SDK path
echo "sdk.dir=/path/to/your/Android/Sdk" > local.properties

# Make gradlew executable (Linux/macOS)
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing)
./gradlew assembleRelease
```

---

## 🔐 Signing for Release

### Generate a Keystore

```bash
keytool -genkey -v -keystore battery-drainer-release.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias batterydrainer
```

You'll be prompted for:
- Keystore password
- Key password  
- Your name, organization, location

**⚠️ SAVE YOUR KEYSTORE AND PASSWORDS! You need them for all future updates.**

### Configure Signing in Gradle

Edit `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../battery-drainer-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "your_password"
            keyAlias = "batterydrainer"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "your_password"
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... rest of config
        }
    }
}
```

### Build Signed APK

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

---

## 📱 Google Play Store Publishing

### Step 1: Create Developer Account

1. Go to: https://play.google.com/console
2. Pay $25 one-time registration fee
3. Complete identity verification (takes 24-48 hours)

### Step 2: Create App Listing

1. **Play Console → Create app**
2. Fill in:
   - App name: `Battery Drainer Benchmark`
   - Default language: English
   - App or Game: App
   - Free or Paid: Free (or Paid for Pro version)

### Step 3: Store Listing Content

#### App Details
```
Short Description (80 chars):
Professional battery benchmarking tool for QA and developers

Full Description (4000 chars):
🔋 Battery Drainer Benchmarker - Professional Battery Testing

Don't guess if your app drains battery. PROVE it.

Battery Drainer is a professional-grade benchmarking tool designed for:
• QA Teams testing app battery impact
• Game developers optimizing power consumption
• Device manufacturers testing hardware
• Power users analyzing battery performance

⚡ FEATURES:

🔥 Multiple Stress Modules
• CPU Stressor - Multi-threaded calculations at various intensities
• GPU Stressor - OpenGL rendering stress test
• Network Stressor - Keep cellular/WiFi radio active
• Sensor Stressor - GPS, Flashlight, Vibration

📊 Real-Time Monitoring
• Live current draw in mA
• Battery voltage tracking
• Temperature monitoring
• Instant thermal state feedback

🎯 Pre-Built Test Profiles
• "The Commute" - GPS + Music streaming simulation
• "The Gamer" - Maximum CPU + GPU load
• "Social Scroll" - Social media simulation
• "Video Call" - Conference call simulation
• And many more!

🛡️ Safety First
• Automatic thermal protection
• Pauses at 45°C, stops at 48°C
• Charging detection auto-pause

📈 Professional Reports
• Discharge curves
• Temperature graphs
• CSV/JSON/HTML export
• Estimated Screen-On Time

🤖 Enterprise Features
• ADB automation support
• Device farm integration
• Headless operation

Perfect for developers who need to know exactly how their apps affect battery life under real-world conditions.

Note: This app intentionally stresses your device. Use responsibly.
```

#### Graphics Assets Needed

| Asset | Size | Description |
|-------|------|-------------|
| App Icon | 512x512 PNG | High-res icon |
| Feature Graphic | 1024x500 PNG | Banner for store listing |
| Screenshots | Min 2 per device | Phone screenshots |
| Phone Screenshots | 16:9 or 9:16 | At least 2 required |

### Step 4: Content Rating

1. Go to **Policy → App content → Content rating**
2. Start questionnaire
3. Answer: No violence, no user data collection, etc.
4. You'll likely get: **Rated for Everyone**

### Step 5: Privacy Policy

Required! Create one at your GitHub or use a generator:

```
Privacy Policy URL: https://stefanrattay1.github.io/BatteryDrainer/privacy
```

Simple privacy policy for Battery Drainer:

```markdown
# Privacy Policy for Battery Drainer

Last updated: December 2025

Battery Drainer does not collect, store, or transmit any personal data.

## Data Collection
- We do NOT collect any personal information
- We do NOT track your location (GPS is used only for stress testing)
- We do NOT send any data to external servers
- All test data stays on your device

## Permissions Used
- Location: Only for GPS stress testing, data is not stored
- Camera: Only for flashlight control
- Internet: Only for network stress testing (downloading test files)
- Vibration: For vibration motor testing

## Contact
For questions: https://github.com/stefanrattay1/BatteryDrainer/issues
```

### Step 6: App Bundle Upload

1. **Release → Production → Create new release**
2. Upload your signed APK or AAB (App Bundle preferred)
3. Add release notes:
   ```
   Version 1.0.0 - Initial Release
   
   • Real-time battery monitoring
   • Multiple stress test profiles
   • Thermal protection system
   • Professional report generation
   • ADB automation support
   ```
4. **Save → Review release → Start rollout**

### Step 7: Review Timeline

- Initial review: 3-7 days for new developers
- Updates: Usually 1-3 days
- May request additional information

---

## 🚀 Quick Commands Summary

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Build App Bundle (preferred for Play Store)
./gradlew bundleRelease

# Clean and rebuild
./gradlew clean assembleRelease

# Run tests
./gradlew test

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📁 Output Locations

| Build Type | Location |
|------------|----------|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `app/build/outputs/apk/release/app-release.apk` |
| App Bundle | `app/build/outputs/bundle/release/app-release.aab` |

---

## ❓ Common Issues

### "SDK location not found"
Create `local.properties` file with your SDK path:
```
sdk.dir=C\:\\Users\\Stefan\\AppData\\Local\\Android\\Sdk
```

### "Could not find gradle wrapper"
Download gradle-wrapper.jar from Android Studio or run:
```bash
gradle wrapper --gradle-version 8.2
```

### "Unsigned APK"
For release builds, ensure you've configured signing in `build.gradle.kts`

---

## 🎉 After Publishing

1. Share on social media
2. Add Play Store badge to README
3. Monitor reviews and crashes in Play Console
4. Plan updates based on user feedback

Good luck with your app! 🚀
