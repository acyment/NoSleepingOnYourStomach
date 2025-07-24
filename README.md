# NoSleepingOnYourStomach

A sleep monitoring Android application that uses device sensors to detect when you're sleeping on your stomach and provides vibration and sound alerts to help you maintain a healthier sleep position.

## Features

- **Real-time orientation monitoring** using device accelerometer, magnetometer, and rotation vector sensors
- **Continuous vibration alerts** when dangerous sleeping position is detected
- **Escalating sound alerts** after 30 seconds in dangerous position
- **Battery optimization** for 8-hour sleep monitoring, especially on older devices
- **Automatic sensor selection** - prioritizes rotation vector sensor for better efficiency
- **Screen-off monitoring** to conserve battery during sleep

## How It Works

The app monitors your phone's orientation using built-in sensors. When you place the phone on your chest and start monitoring:

1. **Safe position**: Roll angle ~0° (screen facing up) - no alerts
2. **Dangerous position**: Roll angle >130° (face down on stomach) - triggers alerts
3. **Alert sequence**:
   - Continuous vibration every 2 seconds (750ms duration)
   - Sound alerts every 3 seconds after 30 seconds in dangerous position
4. **Automatic stop**: All alerts cease immediately when returning to safe position

## Requirements

- Android 4.4 (API 19) or higher
- Device with accelerometer and magnetometer sensors
- Vibration capability (recommended)

## Installation

1. Download the APK from releases
2. Enable "Install from unknown sources" in your device settings
3. Install the APK
4. Grant necessary permissions (vibration)

## Usage

1. Place your phone on your chest, screen facing up
2. Tap "Start Monitoring"
3. The screen will turn off to save battery
4. Sleep normally - the app will alert you if you roll onto your stomach
5. Tap "Stop Monitoring" when you wake up

## Battery Optimization

The app includes several battery-saving features:

- **Screen turns off** during monitoring
- **Optimized sensor polling** rate (200ms intervals)
- **Efficient sensor selection** (rotation vector preferred over accelerometer+magnetometer)
- **Automatic optimization** for devices running Android < 6.0
- **Reduced vibration intensity** on older devices

## Technical Details

- **Language**: Kotlin
- **Build System**: Gradle with Kotlin DSL
- **Architecture**: Single Activity pattern
- **Sensors**: Rotation Vector (primary), Accelerometer + Magnetometer (fallback)
- **Min SDK**: 19 (Android 4.4 KitKat)
- **Target SDK**: 35 (Android 15)

## Development

### Building

```bash
./gradlew assembleDebug    # Build debug APK
./gradlew assembleRelease  # Build release APK
./gradlew clean           # Clean build artifacts
```

### Testing

```bash
./gradlew test                    # Run unit tests
./gradlew connectedAndroidTest    # Run instrumented tests
```

### Linting

```bash
./gradlew lint            # Run Android lint checks
```

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Disclaimer

This app is designed to help improve sleep posture but should not be considered medical advice. Consult with healthcare professionals for sleep-related health concerns.

## Built entirely using Claude Code and aide.chat + DeepSeek R1
