# Location Mock

Minimal Android mock-location control. Free, local, and without ads or tracking.

[![Android CI](https://github.com/Majkey25/Location-Mock/actions/workflows/android.yml/badge.svg)](https://github.com/Majkey25/Location-Mock/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/Majkey25/Location-Mock?include_prereleases&sort=semver)](https://github.com/Majkey25/Location-Mock/releases/tag/v1.0.1-rc.1)

![Location Mock banner](docs/play-store/assets/github-social-preview.png)

## Features

- Exact latitude and longitude input.
- Address and place search through Android's device geocoder.
- Prague, London, New York, and Tokyo presets.
- Persistent mock location through a foreground service.
- One-tap Stop from the app or notification.
- Clear setup, privacy, and about menus.
- Embedded WireGuard client in debug and GitHub builds.
- No ads, analytics, account, or maintainer-operated backend.
- Google Play build has no `INTERNET` permission or VPN service.

## Setup

1. Enable Android Developer options.
2. Open **Select mock location app**.
3. Choose **Location Mock**.
4. Open Location Mock, enter coordinates, and tap **Start mock location**.

Android marks injected coordinates as mock. Apps and websites can detect this. GPS coordinates do not change an IP address. Prices can also depend on account, cookies, payment country, or other signals.

## Address search

Address search results come from Android's device geocoding service and may be unavailable or inaccurate. Search queries are not sent to a maintainer server or stored as history.

## Private network

The GitHub APK embeds the official open-source WireGuard tunnel client. Import one configuration from a WireGuard server you trust, accept Android's VPN prompt, then connect and disconnect inside Location Mock.

Location Mock does not bundle or operate a VPN server. It encrypts the imported configuration in app-private storage with Android Keystore and does not inspect or log tunneled traffic. The Google Play build contains no VPN service or WireGuard dependency.

## Privacy

Coordinates stay in fixed-size local preferences. No location history is kept. The GitHub build stores one imported WireGuard configuration encrypted in app-private storage. Android backup and device transfer are disabled. Read the full [privacy policy](PRIVACY.md).

## Build

Requirements: JDK 17 and Android SDK 37.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug lintGithubRelease assembleDebug assembleGithubRelease bundleRelease --console=plain
```

## Distributions

| Channel | Application ID | Artifact |
|---|---|---|
| Google Play | `com.majkeylab.locationmock` | AAB, address search, no VPN service |
| GitHub | `com.majkeylab.locationmock.github` | APK, address search, embedded WireGuard client |

Google Play re-signs its APKs. The separate GitHub package avoids signature conflicts and makes each channel independently updateable.

### Migration from the first prerelease

The original `v1.0.0` GitHub APK used the Play package ID. Uninstall that sideload before installing Google Play. The corrected GitHub package is a separate fresh install and does not migrate saved coordinates or settings from `v1.0.0`.

## License

Apache License 2.0. See [LICENSE](LICENSE).
