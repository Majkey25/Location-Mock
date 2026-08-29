# Google Play listing

## Main store listing

**App name**

Location Mock

**Short description**

Set a test GPS location with clear controls and no tracking.

**Full description**

Location Mock is a small tool for Android location testing and travel preview.

Search for an address or place, enter latitude and longitude, or choose Prague, London, New York, or Tokyo. Start the mock location, switch to another app, and stop it from Location Mock or, when notifications are allowed, its persistent notification.

Address results come from Android's device geocoding service and may be unavailable or inaccurate. Queries are not stored as history or sent to a maintainer server.

One-time setup uses Android Developer options. Android identifies injected coordinates as mock, so another app may detect or reject them.

GPS coordinates and IP location are different. The Google Play build does not operate a VPN or inspect network traffic.

Privacy:
- No account
- No ads or analytics
- No location history
- No Internet permission
- No maintainer-operated server
- Android backup and device transfer disabled

Changing GPS or IP location does not guarantee different prices or availability. Websites can also use account, cookies, payment country, and other signals.

## Release notes

Address search with validated coordinates, city presets, persistent mock location, notification Stop action, and local-only storage.

## Play Console declarations

- App category: Tools
- Ads: No
- App access: All features available without an account
- Target audience: 18 and over
- Data collected: None
- Data shared: None
- Approximate location permission: Used on device for the foreground mock-location service; not collected or shared
- Privacy policy: `https://majkey25.github.io/Location-Mock/privacy.html`
- Content rating: Utility; no objectionable content
- Government app: No
- Financial features: No
- Health features: No
- VPN service declaration: Not applicable; app does not declare or use `VpnService`

## Foreground service declaration

- Foreground service type: Location
- Functionality: The user taps Start to keep one selected mock GPS coordinate active while testing another app or website.
- Impact if deferred: The mock location is unavailable when the user’s test starts.
- Impact if interrupted: The tested app or website reverts to the device’s real location during the active test.
- User control: Start is always user initiated. The app provides Stop, and its ongoing notification also provides Stop when notifications are allowed.
- Demo video: `https://github.com/Majkey25/Location-Mock/releases/download/v1.0.0/location-mock-fgs-demo.mp4`
