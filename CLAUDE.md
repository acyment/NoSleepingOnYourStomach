# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

**Build and Test:**

```bash
./gradlew build                    # Build the entire project
./gradlew assembleDebug           # Build debug APK
./gradlew assembleRelease         # Build release APK
./gradlew test                    # Run unit tests
./gradlew connectedAndroidTest    # Run instrumented tests on device/emulator
./gradlew clean                   # Clean build artifacts
```

**Linting and Code Quality:**

```bash
./gradlew lint                    # Run Android lint checks
./gradlew ktlintCheck            # Check Kotlin code style (if configured)
```

## Project Architecture

**Application Type:** Single-activity Android sleep monitoring app that uses device sensors to detect stomach sleeping position and provide vibration alerts.

## Caveats

Will run on old hardware (Samsung Galaxy GT-I9195) with Android 4.4.2
Battery might not be on optimal condition, so saving battery is essential for the app to work properly.

**Core Architecture:**

- **Single Activity Pattern:** All functionality contained in `MainActivity.kt`
- **Sensor-Driven:** Uses accelerometer, magnetometer, and rotation vector sensors
- **Real-time Processing:** Continuous orientation monitoring with immediate UI feedback

**Key Components:**

- `MainActivity.kt` (327 lines): Handles sensor management, orientation calculations, vibration control, and UI updates
- Sensor pipeline: Raw sensor data → rotation matrix → orientation angles → sleep position detection
- Vibration system with cooldown periods to prevent excessive alerts

**Technology Stack:**

- **Language:** Kotlin
- **Build System:** Gradle with Kotlin DSL
- **Min SDK:** 19 (Android 4.4), Target SDK: 35 (Android 15)
- **Dependencies:** AndroidX Core KTX, AppCompat, Material Components
- **Hardware:** Requires accelerometer, magnetometer, and rotation vector sensors

**Project Structure:**

```
app/src/main/java/com/cyment/nosleepingonyourstomach/
└── MainActivity.kt               # Main sensor monitoring logic

app/src/main/res/
├── layout/activity_main.xml      # Simple RelativeLayout UI
├── values/                       # Material Dark theme with purple/teal colors
└── drawable/                     # Vector drawables for UI elements
```

**Sensor Data Flow:**

1. Register multiple sensor listeners (accelerometer, magnetometer, rotation vector)
2. Calculate rotation matrix from sensor fusion
3. Extract roll/pitch orientation angles
4. Detect stomach sleeping when roll angle indicates face-down position
5. Trigger vibration alerts with UI status updates

**State Management:**

- Monitoring state (active/idle) controls sensor registration
- Screen stays on during active monitoring
- Automatic sensor cleanup on app pause/destroy

## Important Notes

**Version Management:** Uses Gradle version catalog in `gradle/libs.versions.toml` for centralized dependency versioning.
