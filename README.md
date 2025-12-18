# 🔋 Battery Drainer Benchmarker

**Professional Battery Stress Testing Tool for Android**

A sophisticated battery benchmarking application designed for QA teams, developers, and power users who need to test device battery performance under real-world conditions.

![Android](https://img.shields.io/badge/Android-26%2B-green) ![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue) ![License](https://img.shields.io/badge/License-MIT-yellow)

## 🎯 Purpose

Most developers test on full-battery devices in air-conditioned offices. Real users have 15% battery, are outside in the heat, and have 20 other apps open. This tool simulates these harsh conditions.

**Key Use Cases:**
- QA teams testing app battery impact
- Game studios optimizing power consumption
- Device manufacturers benchmarking hardware
- Developers profiling energy usage

## ✨ Features

### 🔥 Stress Modules

| Module | Description | Power Draw |
|--------|-------------|------------|
| **CPU Stressor** | Multi-threaded Pi calculations, prime finding, matrix operations | 50-200mA/core |
| **GPU Stressor** | OpenGL ES 2.0 rendering with complex shaders | 200-800mA |
| **Network Stressor** | Continuous downloads keeping radio in high-power state | 100-400mA |
| **Sensor Stressor** | GPS, Flashlight, Vibration motor | 100-450mA |

### 📊 Pre-Built Profiles

Profiles are organized by category for easy selection:

#### Baseline Tests
| Profile | Description | Use For |
|---------|-------------|---------|
| 😴 **Idle Baseline** | No load | Reference measurement |
| 📺 **Screen On Only** | Display only | Isolate screen drain |

#### Component Isolation
| Profile | Description | Tests |
|---------|-------------|-------|
| 🔦 **Flashlight** | LED torch | LED drain |
| 📳 **Vibration** | Haptic motor | Motor drain |
| 📍 **GPS Only** | Location polling | GPS module |
| 📶 **Network Only** | Downloads | Modem/WiFi |

#### CPU Stress Tests
| Profile | CPU Load | Simulates |
|---------|----------|-----------|
| 🖥️ **CPU Light** | 25% | Background tasks |
| 💻 **CPU Medium** | 50% | Active app |
| 🔥 **CPU Heavy** | 75% | Intensive work |
| ☢️ **CPU Meltdown** | 100% | Max thermal |

#### GPU Stress Tests
| Profile | GPU Load | Simulates |
|---------|----------|-----------|
| 🎨 **GPU Light** | 25% | 2D UI |
| 🖼️ **GPU Medium** | 50% | Casual 3D |
| 🎮 **GPU Heavy** | 100% | Heavy gaming |

#### Real-World Scenarios
| Profile | Description | What It Tests |
|---------|-------------|---------------|
| 💬 **Messaging** | WhatsApp/Telegram | Light mixed load |
| 📧 **Email Sync** | Background email | Periodic network |
| 🎵 **Music Streaming** | Spotify/YT Music | Audio + network |
| 🎙️ **Podcast** | Audio playback | Minimal drain |
| 📱 **Social Scroll** | Instagram/TikTok | Mixed heavy load |
| 📺 **Video Streaming** | Netflix/YouTube | Decode + network |
| 🌐 **Web Browsing** | Chrome/Firefox | General usage |
| 🚗 **The Commute** | Maps + Music | GPS + audio + screen |
| 🚕 **Rideshare Driver** | Uber/Lyft mode | Continuous GPS |
| 🏃 **Fitness Tracking** | Running apps | GPS + audio |
| 📹 **Video Call** | Zoom/Teams | Encode + network |
| 📞 **Voice Call** | Phone/VoIP | Audio + modem |

#### Gaming Profiles
| Profile | Load Level | Simulates |
|---------|------------|-----------|
| 🧩 **Casual Game** | Light | Candy Crush |
| ⚔️ **Mid-Range Game** | Medium | Clash Royale |
| 🎮 **Heavy Gaming** | Maximum | PUBG/Genshin |
| 🥽 **VR/AR** ⭐ | GPU heavy | Pokemon GO |

#### Productivity
| Profile | Load | Simulates |
|---------|------|-----------|
| 📝 **Document Editing** | Light | Google Docs |
| 🖼️ **Photo Editing** | Medium | Lightroom |
| 🎬 **Video Editing** ⭐ | Heavy | CapCut |

#### Worst-Case Scenarios ⭐
| Profile | Description |
|---------|-------------|
| 🧟 **The Zombie** | Poor signal (power hunting) |
| 📸 **The Photographer** | GPS + heavy processing |
| 📡 **Live Streamer** | Record + encode + upload |
| 💀 **EVERYTHING** | All systems maxed |

⭐ = Premium profiles

### 🛡️ Safety Features

- **Thermal Protection**: Auto-pause at 45°C, stop at 48°C
- **Battery Level Monitoring**: Real-time µA current readings
- **Cooldown Mode**: Automatically resumes when safe
- **Charging Detection**: Pauses test when charger connected

### 📈 Professional Reports

Generated reports include:
- Discharge curve graph
- Temperature over time
- Current draw analysis
- Estimated Screen-On Time (SOT)
- Device information
- Export to JSON, CSV, HTML

## 🚀 Getting Started

### Requirements

- Android 8.0+ (API 26)
- Location permission (for GPS stressor)
- Notification permission (Android 13+)
- Battery optimization exemption (recommended)

### Building

```bash
# Clone the repository
git clone https://github.com/stefanrattay1/BatteryDrainer.git
cd BatteryDrainer

# Build with Gradle
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Basic Usage

1. Launch the app
2. Select a test profile
3. Tap "START TEST"
4. Monitor real-time battery stats
5. Test auto-stops when target drain is reached
6. View generated report

## 🤖 ADB Automation

Perfect for device farms and automated testing pipelines.

### Start a Test

```bash
adb shell am start -n com.batterydrainer.benchmark/.automation.AdbTriggerActivity \
    --es "profile" "commute" \
    --ei "duration" 60 \
    --ei "target_drop" 20
```

### Stop a Test

```bash
adb shell am start -n com.batterydrainer.benchmark/.automation.AdbTriggerActivity \
    --es "action" "stop"
```

### Get Status

```bash
adb shell am start -n com.batterydrainer.benchmark/.automation.AdbTriggerActivity \
    --es "action" "status"
```

### List Available Profiles

```bash
adb shell am start -n com.batterydrainer.benchmark/.automation.AdbTriggerActivity \
    --es "action" "list_profiles"
```

### Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `profile` | String | Profile ID (see list above) |
| `duration` | Integer | Max test duration in minutes |
| `target_drop` | Integer | Stop after X% battery drop |
| `max_temp` | Float | Thermal cutoff temperature (°C) |

## 📱 Permissions

```xml
<!-- Core -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Battery Monitoring -->
<uses-permission android:name="android.permission.BATTERY_STATS" />

<!-- Stressors -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.VIBRATE" />
```

## 🏗️ Architecture

```
com.batterydrainer.benchmark/
├── data/                  # Data models
│   ├── BatteryModels.kt   # Battery readings, thermal state
│   ├── StressProfile.kt   # Profile definitions
│   └── ReportModels.kt    # Report structures
├── stressors/             # Stress modules
│   ├── Stressor.kt        # Base interface
│   ├── CpuStressor.kt     # CPU stress (Pi, primes, matrix)
│   ├── GpuStressor.kt     # OpenGL rendering
│   ├── NetworkStressor.kt # Download stress
│   ├── SensorStressor.kt  # GPS, flash, vibrate
│   └── StressorManager.kt # Coordinates all stressors
├── monitor/               # Monitoring
│   ├── BatteryMonitor.kt  # Battery stats collection
│   └── ThermalProtection.kt # Safety system
├── service/               # Background service
│   └── DrainerService.kt  # Foreground service
├── report/                # Reporting
│   └── ReportGenerator.kt # HTML/JSON/CSV export
├── automation/            # ADB control
│   └── AdbTriggerActivity.kt
└── ui/                    # User interface
    ├── MainActivity.kt
    ├── ProfileActivity.kt
    ├── ReportActivity.kt
    └── SettingsActivity.kt
```

## 💰 Monetization Strategy

### Free Tier
- All basic profiles
- Real-time monitoring
- JSON export

### Pro Tier ($5.99 one-time)
- Premium profiles (Zombie, Photographer, Everything)
- CSV/PDF export
- Extended reporting

### Enterprise ($49.99/year)
- ADB automation support
- Device farm integration
- Priority support

## ⚠️ Safety Warning

This app intentionally stresses your device hardware. While thermal protection is built-in:

1. **Don't leave unattended** during high-stress tests
2. **Test in cool environments** when possible
3. **Don't use on devices with battery issues**
4. **Monitor temperature** closely

**The developers are not responsible for any device damage.**

## 📄 License

MIT License - See [LICENSE](LICENSE) for details.

## 🤝 Contributing

Contributions welcome! Please read our contributing guidelines first.

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## 📧 Support

- **Issues**: [GitHub Issues](https://github.com/stefanrattay1/BatteryDrainer/issues)
- **Author**: [Stefan Rattay](https://github.com/stefanrattay1)
- **Repository**: [github.com/stefanrattay1/BatteryDrainer](https://github.com/stefanrattay1/BatteryDrainer)

---

**Built with ❤️ by [Stefan Rattay](https://github.com/stefanrattay1) for the Android testing community**
