# Privacy policy

Updated: October 5, 2026. The [full privacy policy](https://majkey25.github.io/Location-Mock/privacy.html) is maintained in [docs/privacy.html](docs/privacy.html).

Location Mock has no maintainer-operated data backend, advertising or analytics. This does not mean that user-requested features never send data: Android receives mock coordinates, the device geocoding provider receives address queries, and the GitHub build can route traffic to a user-configured WireGuard server.

The app uses Android approximate-location permission only while its user-started foreground service supplies mock coordinates. The selected coordinates and latest service error stay in fixed-size local app preferences. The app keeps no location history. Android backup and device transfer are disabled.

Location Mock has no advertising SDK, analytics SDK, account system, or maintainer-operated server. The Google Play build has no Internet permission or VPN service.

When you choose Search, Android's device geocoding service receives your query and may contact its network backend, even without Internet permission in the Play app. Use coordinates or presets to avoid this processing. Location Mock does not send address queries to a maintainer server or keep persistent search history. Search text can remain in Android's temporary activity state. Geocoding availability and accuracy are not guaranteed.

Google Play uses `com.majkeylab.locationmock`. The GitHub APK uses `com.majkeylab.locationmock.github` and includes an embedded WireGuard client.

If a user imports a WireGuard configuration in the GitHub build, Location Mock validates it and encrypts it in app-private storage using AES-GCM and a non-exportable Android Keystore key. The maintainer does not receive the imported configuration. It remains until replaced, removed with **Remove configuration**, or app storage is cleared or uninstalled.

While the user connects, Android routes traffic matching the imported configuration to its remote WireGuard endpoint. Location Mock does not operate that endpoint, inspect traffic, keep traffic logs, monetize traffic, or send traffic data to the maintainer. The GitHub build declares Internet and VPN access only for this user-configured tunnel.

Clear app storage or uninstall to remove local coordinates and settings. In the GitHub build, disconnect and choose **Remove configuration** to remove the stored VPN configuration; delete the original imported file separately. The maintainer has no remote copy or app account to delete.

The full policy also covers GitHub Pages hosting, cookies, support correspondence, retention, privacy rights, provider processing and deletion requests. [Terms and third-party notices](https://majkey25.github.io/Location-Mock/terms.html).

Maintainer: Matěj Teplý, publishing as Majkey. Questions and requests: [majkeylab@gmail.com](mailto:majkeylab@gmail.com).
