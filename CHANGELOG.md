# Changelog

## 1.1.0-rc.2 - 2026-10-05

- Explain device geocoding and chosen VPN-provider processing before use.
- Link full privacy, deletion, terms and third-party notices from the app.
- Add accessible policy navigation and correct outdated third-party notices.

## 1.1.0-rc.1 - 2026-08-29

### Added

- Address and place search through Android's device geocoder.
- Embedded WireGuard import, Connect, and Disconnect in debug and GitHub builds.
- Android Keystore AES-GCM protection for the imported WireGuard configuration.

### Changed

- Removed the external VPN handoff.
- Kept the Google Play release free of VPN services and WireGuard dependencies.

## 1.0.1-rc.1 - 2026-08-28

### Changed

- GitHub APK now uses `com.majkeylab.locationmock.github` to avoid Google Play signing conflicts.
- Existing `v1.0.0` sideloads require uninstall before switching to Google Play; the separate GitHub package starts with fresh local settings.

## 1.0.0 - 2026-08-28

### Added

- Persistent mock GPS location with validated coordinates.
- Four city presets and one-tap Stop action.
- English Material 3 interface with Setup, Privacy, and About menus.
- Local-only storage, Android backup disabled, and no Internet permission.
