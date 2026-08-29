# Privacy policy

Effective date: August 29, 2026

Location Mock does not collect, transmit, sell, or share personal data.

The app uses Android approximate-location permission only while its user-started foreground service supplies mock coordinates. The selected coordinates and latest service error stay in fixed-size local app preferences. The app keeps no location history. Android backup and device transfer are disabled.

Location Mock has no advertising SDK, analytics SDK, account system, or maintainer-operated server. The Google Play build has no Internet permission or VPN service.

Address searches may be processed by Android's device geocoding service. Location Mock does not send address queries to a maintainer server or keep search history. Geocoding availability and accuracy are not guaranteed.

Google Play uses `com.majkeylab.locationmock`. The GitHub APK uses `com.majkeylab.locationmock.github` and includes an embedded WireGuard client.

If a user imports a WireGuard configuration in the GitHub build, Location Mock validates it and encrypts it in app-private storage using AES-GCM and a non-exportable Android Keystore key. Private keys and endpoints are not written to logs. The configuration is removed only when the user chooses **Remove configuration** or clears app data.

While the user connects, Android routes traffic matching the imported configuration to its remote WireGuard endpoint. Location Mock does not operate that endpoint, inspect traffic, keep traffic logs, monetize traffic, or send traffic data to the maintainer. The GitHub build declares Internet and VPN access only for this user-configured tunnel.

Questions: [majkeylab@gmail.com](mailto:majkeylab@gmail.com)
