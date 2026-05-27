# Loupe - Magnifier App

A survival toolkit magnifying glass app for Android using the rear camera as a magnifier with zoom capabilities.

## Features

- **Live Camera Preview**: Uses the rear camera to provide a real-time magnified view
- **Zoom Slider**: Adjustable zoom from 1x to maximum device zoom level
- **Flashlight Toggle**: Turn on/off the LED flash for illumination in low-light conditions
- **Freeze/Capture**: Pause the live view to examine the frozen image
- **Magnifier Circle Overlay**: Visual guide showing the magnified area
- **Material Design 3**: Modern UI with Jetpack Compose

## Color Scheme

- **Background**: `#1A1A2E` (Dark Navy)
- **Accent**: `#4A9FFF` (Blue)
- **Secondary**: `#0F3460` (Navy)
- **Icon Background**: `#000000` (Black)

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Camera**: CameraX (camera-camera2, camera-lifecycle, camera-view)
- **Min SDK**: 26
- **Target SDK**: 35
- **Compile SDK**: 35
- **Java Version**: 17

## Permissions

- `android.permission.CAMERA` - Required for camera access
- `android.permission.FLASHLIGHT` - Required for flashlight control

## Project Structure

```
loupe/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── kotlin/com/survival/magnifier/
│   │       │   ├── MainActivity.kt
│   │       │   └── ui/
│   │       │       ├── LoupeApp.kt
│   │       │       └── MagnifierScreen.kt
│   │       ├── res/
│   │       │   ├── drawable/
│   │       │   │   └── ic_launcher.xml
│   │       │   └── values/
│   │       │       ├── strings.xml
│   │       │       └── themes.xml
│   │       └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── .gitignore
```

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew installDebug
```

## Dependencies

- androidx.compose (BOM 2024.11.00)
- androidx.camera:camera-camera2:1.4.0
- androidx.camera:camera-lifecycle:1.4.0
- androidx.camera:camera-view:1.4.0
- androidx.core:core-ktx:1.15.0
- androidx.lifecycle:lifecycle-runtime-ktx:2.8.7
- androidx.activity:activity-compose:1.9.3

## License

Created for survival toolkit applications.
