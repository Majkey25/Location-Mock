# Address Search and Embedded WireGuard Design

## Goal

Add address search to every Location Mock build. Remove all Proton VPN integration. Add an embedded, open-source WireGuard client to the GitHub and debug builds without placing `VpnService` in the Google Play release.

## Constraints

- Keep `com.majkeylab.locationmock` as the Google Play application ID.
- Keep `com.majkeylab.locationmock.github` as the GitHub application ID.
- Preserve the existing mock-location workflow and foreground service.
- Keep the Google Play artifact free of `VpnService`, VPN tunnel code, and VPN permissions.
- Do not bundle public VPN endpoints, private keys, credentials, or provider accounts.
- Do not claim that unknown public VPN servers are safe or reliable.
- A VPN needs a trusted remote WireGuard endpoint. The app supplies the client only.
- After one configuration import, connection and disconnection must be available inside Location Mock.

## Distribution Model

### Google Play release

The Play release gains address search and removes the Proton handoff. It contains no VPN implementation or VPN service declaration. This preserves the current personal Play account and avoids replacing the active review with a VpnService declaration that the account cannot currently satisfy.

### GitHub and debug releases

The GitHub and debug releases gain address search plus the embedded WireGuard client. VPN code, dependency, service manifest, and UI provider live in VPN-only source sets. The GitHub package remains installable beside the Play package.

## Address Search

### User flow

1. Enter an address, venue, city, airport code, or place name.
2. Tap **Search**.
3. See at most five labeled results.
4. Tap one result to copy its latitude and longitude into the existing fields.
5. Start mock location through the unchanged flow.

### Implementation

- Add `AddressSearchRepository` backed by Android `Geocoder`.
- Require a trimmed query between 3 and 200 characters.
- Request at most five results.
- Use the asynchronous `GeocodeListener` API on Android 13 and newer.
- Run the legacy synchronous API on `Dispatchers.IO` for Android 10–12.
- Return immutable `AddressSearchResult` values containing label, latitude, and longitude.
- Report geocoder unavailable, no result, invalid query, timeout, and I/O failure explicitly.
- Never block the main thread.
- Do not add a map SDK or a separate geocoding dependency.

Android geocoding can use a device or network provider. The UI and privacy policy must state that address queries may be processed by the device geocoding service and that results are not guaranteed.

## Embedded WireGuard

### User flow

1. Open **Private network** in the GitHub/debug build.
2. Import one trusted WireGuard `.conf` file using Android's document picker.
3. Read a prominent disclosure explaining that the VPN routes device traffic to the endpoint named in the imported file and that Location Mock does not operate that endpoint.
4. Confirm Android's system VPN dialog.
5. Tap **Connect** or **Disconnect** inside Location Mock.

The app has no default provider. A trusted configuration is required once before the switch can connect.

### Tunnel implementation

- Pin the official embeddable WireGuard Android tunnel library from the WireGuard project.
- Use the library's userspace backend and Android `VpnService` integration.
- Support one tunnel named `Location Mock`.
- Use full-tunnel routes only when the imported configuration declares them.
- Display tunnel state: no configuration, disconnected, connecting, connected, disconnecting, or error.
- Show Android's system-managed VPN notification while connected.
- Stop cleanly on user disconnect, VPN revocation, process shutdown, or tunnel failure.
- Do not inspect, log, monetize, redirect, or modify tunneled traffic.

### Configuration security

- Validate the imported configuration with WireGuard's parser before storing it.
- Reject files without a private key, peer, endpoint, or allowed IPs.
- Encrypt the configuration with AES-GCM using a non-exportable Android Keystore key.
- Store only the encrypted blob and IV in app-private storage.
- Keep Android backup and device transfer disabled.
- Never print the configuration, keys, endpoint credentials, or tunnel packets.
- Provide **Remove configuration**, which disconnects first and deletes the encrypted file.

## Build Isolation

- Let the main screen call one build-specific `VpnFeatureSection` composable.
- Put a one-line no-op `VpnFeatureSection` in `src/release`.
- Put the real UI, controller, state, encrypted store, strings, and WireGuard dependency in VPN-only Kotlin source directories shared by `debug` and `githubRelease` through AGP 9's `AndroidSourceSet.kotlin` set.
- Merge the VPN service manifest only into `debug` and `githubRelease`.
- Use `debugImplementation` and `githubReleaseImplementation`; never `implementation` for WireGuard.
- Remove the Proton package query, launch code, strings, listing text, and privacy text.

This isolation must be verified by inspecting the merged Play manifest and release AAB. The Play artifact must not contain `android.net.VpnService`, `BIND_VPN_SERVICE`, WireGuard classes, or the VPN UI.

## UI

- Preserve the current single-screen Material 3 layout.
- Add one compact address field and **Search** action above the coordinate fields.
- Show results as simple selectable rows, not nested cards.
- Disable address and coordinate editing while mock location is active.
- Replace **Change IP location** with **Private network** only in VPN-capable builds.
- Show import/remove configuration plus one primary Connect/Disconnect action.
- Keep the pricing warning: GPS/IP changes do not guarantee prices or availability.

## Documentation and Store Metadata

- Version: `1.1.0-rc.1`, version code `3`.
- Update README, changelog, privacy policy, Play listing, GitHub release notes, screenshots, and manifest documentation.
- Play listing: address search, no embedded VPN claim, no Proton text.
- GitHub documentation: embedded WireGuard client, bring-your-own trusted configuration, no operated server.
- Privacy policy: device geocoder behavior, encrypted VPN configuration, traffic routing, no traffic inspection/logging, and build differences.
- Data Safety remains no developer collection or sharing for the Play build; review the declaration before release.

## Verification

### Static gates

- Existing unit tests.
- New focused tests for query validation, address-result mapping, VPN configuration-store round trip, and invalid configuration rejection.
- Android lint with warnings as errors.
- Clean debug, Play release bundle, and GitHub release APK builds.
- Verify Play merged manifest and AAB contain no VPN service or WireGuard code.
- Verify GitHub APK package, signing, service declaration, and WireGuard dependency.

### Live address scenarios

1. Valid venue/address returns selectable results and updates coordinates.
2. Ambiguous query returns multiple results without auto-selecting one.
3. Empty, invalid, offline, or unavailable geocoder returns an explicit error.
4. Existing coordinate presets and direct numeric entry still work.

### Live VPN scenarios

1. Valid configuration imports and survives process restart encrypted.
2. Invalid configuration is rejected without replacing the valid one.
3. VPN consent denied leaves the tunnel disconnected.
4. Connect and disconnect update Android and app state without leaks.
5. Process death, revoke, and network loss stop or recover cleanly.

Full live tunnel verification requires a trusted WireGuard test endpoint supplied for QA. Without one, build/import/permission/failure checks can pass, but successful traffic routing must remain unverified.

### Devices

- Android emulator for repeatable permission and failure flows.
- Authorized `YAL-L21` only for physical QA, with every ADB command pinned to `BQLDU19927002646`.
- Do not modify or test any other physical device.

## Release Boundary

- Do not commit, tag, push, publish, or modify Play Console until explicitly approved after implementation and verification.
- Keep the existing Play review untouched during development.
