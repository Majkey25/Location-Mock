# Location Mock design

## Objective

Build a free, offline-first Android app that keeps a user-selected mock GPS location active until stopped. The app also explains that GPS and IP location differ and can open Proton VPN as a separate, trusted free VPN option. It never promises lower prices or bypass of another service's rules.

## Product scope

- App label: `Location Mock`
- Application ID and namespace: `com.majkeylab.locationmock`
- Android 10 minimum, Android 17 target; verified on Android 16 target phone
- English-only UI
- No ads, analytics, account, backend, map SDK, or `INTERNET` permission
- One screen: current status, latitude/longitude fields, city presets, Start/Stop action, setup guidance, and IP/VPN handoff
- Overflow menu: Setup, Privacy, About
- Proton VPN handoff uses its installed package or Google Play listing. No VPN traffic passes through Location Mock.

## Architecture

- `Coordinates` owns numeric parsing and range validation.
- `MockLocationService` is a user-started foreground location service. It keeps Google Play services Fused Location Provider in mock mode and publishes a fresh timestamped location every second.
- `MainActivity` hosts one Material 3 Compose screen.
- `SharedPreferences` stores only last coordinates and current service status. Size is fixed.
- Android Developer options remain the trust boundary. The user must select Location Mock as the mock location app.

## Tech stack

- Kotlin 2.3.21, JVM 17
- Android Gradle Plugin 9.3.2, Gradle 9.7.1
- compile/target SDK 37, min SDK 29
- Jetpack Compose with BOM 2026.08.00 and Material 3
- Google Play services Location 21.4.0
- JUnit 4 for coordinate validation

## Commands

- Unit tests: `./gradlew.bat testDebugUnitTest --console=plain`
- Lint: `./gradlew.bat lintDebug --console=plain`
- Debug build: `./gradlew.bat assembleDebug --console=plain`
- Release bundle: `./gradlew.bat bundleRelease --console=plain`
- Device install: `adb -s <S25-ULTRA-SERIAL> install --user 0 -r app/build/outputs/apk/debug/app-debug.apk`
- Device launch: `adb -s <S25-ULTRA-SERIAL> shell am start -n com.majkeylab.locationmock/.MainActivity`

## Project structure

- `app/src/main/java/com/majkeylab/locationmock` -> app, UI, validation, foreground service
- `app/src/main/res` -> English strings, theme, icons
- `app/src/test` -> bounded coordinate validation tests
- `docs/play-store` -> listing copy and generated store assets
- `docs/privacy.html` and `PRIVACY.md` -> public privacy policy
- `.github/workflows/android.yml` -> reproducible build gates

## Testing strategy

- Unit test valid coordinates, decimal comma input, latitude/longitude boundaries, invalid text, and out-of-range values.
- Run Gradle test, lint, debug APK, and release AAB gates.
- On Galaxy S25 Ultra only: verify setup failure before mock authorization, happy path, boundary coordinate, invalid input, Stop restoration, menu actions, Proton handoff, process state, notification, `dumpsys location`, and crash buffer.
- Verify Chrome geolocation separately. IP stays unchanged until a VPN connects.

## Boundaries

- Always: pin ADB to the approved Galaxy S25 Ultra serial; validate coordinates before service start; expose Stop; clear mock mode on service shutdown; keep claims accurate.
- Ask first: none for approved implementation/release scope. Stop if signing or Play Console requires a secret or legal declaration that cannot be inferred safely.
- Never: touch the connected Huawei; bundle public relay servers; claim guaranteed savings; hide mock status; collect browsing/location history; commit secrets.

## Success criteria

- User can select valid coordinates and keep them active while another app is foreground.
- Invalid input cannot start the service and shows a precise English error.
- Stop exits mock mode and removes the foreground notification.
- App contains no `INTERNET` permission and sends no data.
- GitHub contains source, CI, privacy, listing assets, signed release artifacts, and release notes.
- Google Play submission is completed when console access and review gates permit it; external review time is reported, never called complete early.
