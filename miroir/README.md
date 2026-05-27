# Miroir - Mirror Survival Toolkit App

Miroir is a survival toolkit Android app that turns your device's front camera into a mirror. Built with Kotlin and Jetpack Compose.

## Features

- **Front Camera Mirror**: Full-screen real-time mirror using the front-facing camera
- **Freeze/Capture**: Pause the camera view to freeze the current frame
- **Mirror Toggle**: Switch between mirrored and non-mirrored view
- **Brightness Control**: Adjust screen brightness with a slider (0.3x - 1.5x)
- **Modern UI**: Dark theme with accent blue color scheme
- **CameraX Integration**: Uses modern Android camera APIs

## Colors

- Background: `#1A1A2E` (Dark Navy)
- Accent Blue: `#4A9FFF`
- Dark Navy: `#0F3460`

## Permissions

- `CAMERA` - Required to access the front camera

## Build & Run

```bash
./gradlew build
./gradlew installDebug
```

## Architecture

- **MainActivity**: Entry point activity with Compose UI
- **CameraManager**: Handles CameraX setup and camera lifecycle
- **MirrorScreen**: Main UI composable with all controls
- **Theme**: Material 3 dark theme with custom colors

## Dependencies

- Jetpack Compose (2024.11.00)
- CameraX (1.4.0)
- Material 3
- Kotlin 1.9+

## Package

`com.survival.mirror`

## App Name

Miroir (French for "Mirror")
