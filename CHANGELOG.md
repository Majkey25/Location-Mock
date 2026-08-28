# Changelog

## 1.0.1-rc.1 - 2026-08-28

### Changed

- GitHub APK now uses `com.majkeylab.locationmock.github` to avoid Google Play signing conflicts.
- Existing `v1.0.0` sideloads require uninstall before switching to Google Play; the separate GitHub package starts with fresh local settings.

## 1.0.0 - 2026-08-28

### Added

- Persistent mock GPS location with validated coordinates.
- Four city presets and one-tap Stop action.
- English Material 3 interface with Setup, Privacy, and About menus.
- Trusted external VPN handoff without handling network traffic.
- Local-only storage, Android backup disabled, and no Internet permission.
