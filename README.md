# Location Mock

Minimal Android mock-location control. Free, local, and without ads or tracking.

[![Android CI](https://github.com/Majkey25/Location-Mock/actions/workflows/android.yml/badge.svg)](https://github.com/Majkey25/Location-Mock/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/Majkey25/Location-Mock?include_prereleases&sort=semver)](https://github.com/Majkey25/Location-Mock/releases/tag/v1.0.0)

![Location Mock banner](docs/play-store/assets/github-social-preview.png)

## Features

- Exact latitude and longitude input.
- Prague, London, New York, and Tokyo presets.
- Persistent mock location through a foreground service.
- One-tap Stop from the app or notification.
- Clear setup, privacy, and about menus.
- Separate Proton VPN handoff for users who also need to change IP location.
- No `INTERNET` permission, ads, analytics, account, or backend.

## Setup

1. Enable Android Developer options.
2. Open **Select mock location app**.
3. Choose **Location Mock**.
4. Open Location Mock, enter coordinates, and tap **Start mock location**.

Android marks injected coordinates as mock. Apps and websites can detect this. GPS coordinates do not change an IP address. Prices can also depend on account, cookies, payment country, or other signals.

## VPN handoff

Location Mock does not run a VPN or inspect traffic. The VPN button opens Proton VPN if installed, otherwise its Google Play page. Proton VPN is a separate service with its own terms. Location Mock is not affiliated with Proton AG.

## Privacy

Coordinates stay in fixed-size local preferences. No location history is kept. Android backup and device transfer are disabled. Read the full [privacy policy](PRIVACY.md).

## Build

Requirements: JDK 17 and Android SDK 37.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug bundleRelease --console=plain
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
