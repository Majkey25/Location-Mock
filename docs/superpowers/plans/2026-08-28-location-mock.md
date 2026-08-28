# Location Mock implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and release a minimal Android mock-location app with a safe external VPN handoff.

**Architecture:** Compose renders one screen. A foreground service owns Fused Location Provider mock mode. A small validated coordinate type is the only domain model; process-local state reports running/stopping and fixed-size preferences keep coordinates/latest error.

**Tech Stack:** Kotlin 2.3.21, AGP 9.3.2, Gradle 9.7.1, Compose Material 3, Google Play services Location 21.4.0, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-28-location-mock-design.md`

## Global constraints

- Play package must stay `com.majkeylab.locationmock`; GitHub APK uses `com.majkeylab.locationmock.github`.
- UI and store copy must stay English.
- No backend, map SDK, ads, analytics, account, or `INTERNET` permission.
- ADB must target Galaxy S25 Ultra only.
- No secrets in Git or command output.

---

### Task 1: Reproducible Android project and RED test

**Files:**
- Create: Gradle wrapper/config, `app/build.gradle.kts`, manifest, resources, `.gitignore`
- Test: `app/src/test/java/com/majkeylab/locationmock/CoordinatesTest.kt`

**Interfaces:**
- Produces expected API `Coordinates.parse(latitude: String, longitude: String): Coordinates`.

- [ ] Create project using versions pinned in spec and existing app-family Gradle layout.
- [ ] Write tests asserting `50.0755, 14.4378`, decimal commas, exact boundaries, invalid text, and range failures.
- [ ] Run `./gradlew.bat testDebugUnitTest --tests "*.CoordinatesTest" --console=plain`.
- [ ] Confirm compilation fails because `Coordinates` does not exist.

### Task 2: Coordinate model GREEN

**Files:**
- Create: `app/src/main/java/com/majkeylab/locationmock/Coordinates.kt`

**Interfaces:**
- Produces `Coordinates.parse(String, String): Coordinates` and `Coordinates.of(Double, Double): Coordinates`.

- [ ] Implement finite numeric parsing with comma normalization.
- [ ] Reject latitude outside `-90.0..90.0` and longitude outside `-180.0..180.0` with user-facing `IllegalArgumentException` messages.
- [ ] Run focused test and confirm all cases pass.

### Task 3: Mock location foreground service

**Files:**
- Create: `MockLocationService.kt`, notification/vector resources
- Modify: `AndroidManifest.xml`, `app/build.gradle.kts`

**Interfaces:**
- Consumes validated latitude/longitude extras.
- Produces explicit `start(context, coordinates)` and `stop(context)` companion methods plus fixed preference keys for UI status.

- [ ] Declare only coarse location, mock location, foreground service/location, and notification permissions.
- [ ] Start foreground notification before entering mock mode.
- [ ] Call `setMockMode(true)`, then publish timestamped mock fixes every 1000 ms.
- [ ] On Stop/destroy remove callbacks, call `setMockMode(false)`, clear fixed status preferences, and stop foreground service.
- [ ] Build debug APK and inspect merged manifest for absence of `android.permission.INTERNET`.

### Task 4: Minimal Material 3 UI

**Files:**
- Create: `MainActivity.kt`, `LocationMockScreen.kt`, `AppTheme.kt`, string/theme/icon resources

**Interfaces:**
- Consumes `Coordinates.parse`, service start/stop methods, process-local service state, and bounded coordinate/error preferences.

- [ ] Build centered responsive screen with status, two outlined numeric fields, four city presets, and one Start/Stop button.
- [ ] Request coarse location only at user Start. Request notification permission independently where available.
- [ ] Detect mock-location app operation. Open Developer options when not authorized.
- [ ] Add Setup, Privacy, About overflow dialogs.
- [ ] Add IP section that opens installed Proton VPN or its Google Play listing and states `No affiliation`.
- [ ] Build and run unit tests, lint, and debug assembly.

### Task 5: Docs, assets, CI, signing, and release

**Files:**
- Create: `README.md`, `PRIVACY.md`, `CHANGELOG.md`, `LICENSE`, `docs/privacy.html`, `.github/workflows/android.yml`
- Create: `docs/play-store/listing.md`, feature graphic, phone screenshots, GitHub social preview

**Interfaces:**
- Produces GitHub release APK/checksums and Google Play AAB/store listing.

- [ ] Generate restrained pin/crosshair icon, 1024x500 Play feature graphic, 1280x640 GitHub graphic, and verified phone screenshots.
- [ ] Configure ignored release keystore properties without printing secrets.
- [ ] Run fresh unit tests, lint, debug APK, and release bundle.
- [ ] Install only on Galaxy S25 Ultra and run all live scenarios from spec.
- [ ] Review `git diff`, scan for secrets/build junk, commit approved scope, push public repo, and verify CI.
- [ ] Create GitHub release with signed APK, SHA-256, and release notes.
- [ ] Submit Play listing/AAB when console gates allow it and report exact track/review state.
