# Vitals

An offline device performance and battery monitor for Android, tuned for the Samsung Galaxy S25.

- Battery health and degradation over time, estimated without root
- Live temperature, thermal throttling status, RAM and CPU frequency
- Per-app screen time and opens
- Customisable home screen widgets (Glance, Material You)
- No internet permission. Nothing leaves the phone.

Full product and technical scope: [docs/SCOPE.md](docs/SCOPE.md).

## Get the APK
Every push runs **Android CI** in GitHub Actions. Open the latest run, download the `vitals-apks` artifact, and install `app-debug.apk` on the phone (enable *Install unknown apps* for your browser or file manager).

## Build locally
Requires Android Studio (Ladybug or newer) or the Android SDK with JDK 17.

```
./gradlew :core:test          # pure-Kotlin maths, runs anywhere
./gradlew :app:assembleDebug  # needs the Android SDK
./gradlew installDebug        # with the phone connected over USB/Wi-Fi debugging
```

## First run on the phone
1. Open Vitals, go to **Apps**, tap **Open settings**, allow Usage data access.
2. **Settings > Open App info > Battery > Unrestricted** so sampling is not paused by One UI.
3. Long-press the home screen, **Widgets > Vitals**, pick a layout.

## Project layout
```
core/   Pure Kotlin: battery capacity estimation, unit normalisation, usage time, retention (unit tested)
app/    Android: sensors, Room storage, WorkManager sampling, Compose UI, Glance widgets
```
