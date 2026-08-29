# Embedded WireGuard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Add an encrypted, in-app WireGuard import/connect flow to debug and GitHub builds while proving that the Play release contains no VPN service or WireGuard dependency.

**Architecture:** LocationMockScreen calls one build-specific VpnFeatureSection composable. Release compiles a one-line no-op section; debug and githubRelease compile the real UI, controller, encrypted store, resources, and official WireGuard tunnel library from shared VPN-only source directories.

**Tech Stack:** Kotlin 2.3.21, Android SDK 29-37, Android VpnService, Android Keystore, WireGuard tunnel 1.0.20260102, coroutines, Compose Material 3, JUnit 4.

**Spec:** docs/superpowers/specs/2026-08-29-location-search-wireguard-design.md

## Global Constraints

- WireGuard dependency is exactly com.wireguard.android:tunnel:1.0.20260102.
- Play releaseRuntimeClasspath must not include WireGuard.
- Play merged manifest must not contain android.net.VpnService or BIND_VPN_SERVICE.
- GitHub/debug use the library-provided GoBackend VpnService.
- Bundle no server, endpoint, private key, credentials, or provider account.
- Import at most 262144 bytes.
- Encrypt the stored configuration with AES-GCM and a non-exportable Android Keystore key.
- Inspect and log no tunnel traffic or configuration contents.
- Keep backup and device transfer disabled.
- Do not commit, tag, push, publish, or modify Play Console.

---

## File Structure

- Create app/src/release/java/com/majkeylab/locationmock/VpnFeatureSection.kt: one-line no-op Compose entry point.
- Create app/src/vpn/java/com/majkeylab/locationmock/VpnController.kt: VPN-only state and controller interface.
- Create app/src/vpn/java/com/majkeylab/locationmock/VpnControllerProvider.kt: singleton real provider.
- Create app/src/vpn/java/com/majkeylab/locationmock/WireGuardVpnController.kt: import, validation, backend state.
- Create app/src/vpn/java/com/majkeylab/locationmock/EncryptedVpnConfigStore.kt: bounded encrypted storage and Android key creation.
- Create app/src/test/java/com/majkeylab/locationmock/EncryptedVpnConfigStoreTest.kt.
- Create app/src/vpn/java/com/majkeylab/locationmock/VpnFeatureSection.kt: real consent, import, state, and controls.
- Create app/src/vpn/res/values/strings.xml: VPN-only strings.
- Modify app/build.gradle.kts, LocationMockScreen.kt, README.md, CHANGELOG.md, docs/privacy.html, and docs/play-store/listing.md.

### Task 1: Variant Isolation

**Interfaces:**

- Produces build-specific composable VpnFeatureSection().
- Release implementation renders no UI and imports no VPN types.
- Debug and GitHub implementation is added in Task 4.

- [ ] **Step 1: Add the release no-op entry point**

    @Composable
    fun VpnFeatureSection() = Unit

- [ ] **Step 2: Configure shared VPN sources and exact dependencies**

Inside android sourceSets:

    getByName("debug").kotlin.directories.add(project.file("src/vpn/java").absolutePath)
    getByName("debug").res.directories.add(project.file("src/vpn/res").absolutePath)
    getByName("githubRelease").kotlin.directories.add(project.file("src/vpn/java").absolutePath)
    getByName("githubRelease").res.directories.add(project.file("src/vpn/res").absolutePath)

Inside dependencies:

    debugImplementation("com.wireguard.android:tunnel:1.0.20260102")
    githubReleaseImplementation("com.wireguard.android:tunnel:1.0.20260102")

Do not add implementation or releaseImplementation.

- [ ] **Step 3: Prove release dependency isolation**

    .\gradlew.bat app:dependencies --configuration releaseRuntimeClasspath --console=plain | Select-String -Pattern "wireguard"
    .\gradlew.bat app:dependencies --configuration githubReleaseRuntimeClasspath --console=plain | Select-String -Pattern "wireguard"

Expected: release output has no match; GitHub output contains tunnel 1.0.20260102.

- [ ] **Step 4: Compile the Play release entry point**

    .\gradlew.bat compileReleaseKotlin --console=plain

Expected: the Play release compiles with the no-op entry point and no WireGuard classes. Debug and GitHub compile after the real entry point is added in Task 4.

### Task 2: Encrypted Bounded Configuration Store

**Interfaces:**

- Produces EncryptedVpnConfigStore(file: File, key: SecretKey).
- Produces read(): ByteArray?, write(cleartext: ByteArray), clear().
- Produces AndroidVpnKey.getOrCreate(): SecretKey.

- [ ] **Step 1: Write failing round-trip, tamper, and clear tests**

Use KeyGenerator.getInstance("AES") in JVM tests and a temporary file.

    @Test
    fun roundTripsEncryptedBytes() {
        val cleartext = "[Interface]\nPrivateKey = test".encodeToByteArray()
        store.write(cleartext)
        assertContentEquals(cleartext, store.read())
        assertFalse(file.readBytes().contentEquals(cleartext))
    }

    @Test
    fun rejectsTamperedCiphertext() {
        store.write("secret".encodeToByteArray())
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
        file.writeBytes(bytes)
        assertFailsWith<GeneralSecurityException> { store.read() }
    }

    @Test
    fun clearDeletesEncryptedFile() {
        store.write("secret".encodeToByteArray())
        store.clear()
        assertNull(store.read())
    }

- [ ] **Step 2: Confirm tests fail**

    .\gradlew.bat testDebugUnitTest --tests com.majkeylab.locationmock.EncryptedVpnConfigStoreTest --console=plain

Expected: compilation fails because EncryptedVpnConfigStore does not exist.

- [ ] **Step 3: Implement AES-GCM file format**

Use a four-byte magic value LMWG, one-byte format version 1, one-byte IV length, IV bytes, then ciphertext/tag bytes. Reject files larger than 262200 bytes, wrong magic/version, IV lengths outside 12-16 bytes, or empty ciphertext.

    class EncryptedVpnConfigStore(
        private val file: File,
        private val key: SecretKey,
    ) {
        fun write(cleartext: ByteArray)
        fun read(): ByteArray?
        fun clear()
    }

Encryption:

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val ciphertext = cipher.doFinal(cleartext)

Decryption uses GCMParameterSpec(128, iv). Write to a sibling temporary file, flush and sync it, then replace the target. Delete the temporary file on every failure. Clear caller-owned plaintext buffers in finally blocks inside the controller.

- [ ] **Step 4: Implement Android Keystore key creation**

Use AndroidKeyStore with alias location_mock_wireguard, AES-256, PURPOSE_ENCRYPT or PURPOSE_DECRYPT, BLOCK_MODE_GCM, ENCRYPTION_PADDING_NONE, and randomized encryption required. Return the existing SecretKeyEntry key or generate it once.

- [ ] **Step 5: Run focused and full unit tests**

    .\gradlew.bat testDebugUnitTest --tests com.majkeylab.locationmock.EncryptedVpnConfigStoreTest --console=plain
    .\gradlew.bat testDebugUnitTest --console=plain

Expected: encryption tests and existing tests pass.

### Task 3: Real WireGuard Controller

**Interfaces:**

- Consumes Config.parse(InputStream), GoBackend.setState, Tunnel.State, and EncryptedVpnConfigStore.
- Implements every VpnController method.
- Produces no traffic logs.

- [ ] **Step 1: Add the VPN-only controller contract**

    enum class VpnUiState {
        NO_CONFIG,
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        ERROR,
    }

    interface VpnController {
        val state: VpnUiState
        val error: String?
        val hasConfiguration: Boolean

        fun permissionIntent(): Intent?
        suspend fun importConfiguration(input: InputStream): Result<Unit>
        suspend fun connect(): Result<Unit>
        suspend fun disconnect(): Result<Unit>
        suspend fun removeConfiguration(): Result<Unit>
    }

This file lives under src/vpn and is absent from the Play release.

- [ ] **Step 2: Add bounded input reading**

Read at most 262145 bytes using an 8192-byte buffer. If more than 262144 bytes are read, fail with WireGuard configuration is too large. Always close only streams opened by the Activity; the controller receives and consumes the supplied stream but does not retain it.

- [ ] **Step 3: Parse and validate before replacing stored config**

    val bytes = input.readBounded(MAX_CONFIG_BYTES)
    val config = Config.parse(ByteArrayInputStream(bytes))
    require(config.peers.isNotEmpty()) {
        "WireGuard configuration needs at least one peer."
    }
    require(config.peers.any {
        it.endpoint.isPresent && it.allowedIps.isNotEmpty()
    }) {
        "WireGuard configuration needs a peer endpoint and allowed IPs."
    }

Only after validation succeeds, encrypt and replace the prior configuration. Never include parser input, keys, or endpoint values in error messages or logs.

- [ ] **Step 4: Implement process-singleton backend and Tunnel**

Use GoBackend(applicationContext). Tunnel name is LocationMock, which satisfies the library's 15-character constraint. Tunnel.onStateChange maps UP to CONNECTED and DOWN to DISCONNECTED or NO_CONFIG.

- [ ] **Step 5: Implement permission and state operations**

permissionIntent returns android.net.VpnService.prepare(context).

connect:

    state = VpnUiState.CONNECTING
    val bytes = store.read() ?: error("Import a WireGuard configuration first.")
    val config = Config.parse(ByteArrayInputStream(bytes))
    backend.setState(tunnel, Tunnel.State.UP, config)
    state = VpnUiState.CONNECTED

disconnect calls backend.setState(tunnel, Tunnel.State.DOWN, null). removeConfiguration disconnects first, clears encrypted storage, then reports NO_CONFIG. Run backend and crypto work on Dispatchers.IO. Convert failures to ERROR with a short sanitized message; preserve no Throwable that contains configuration material.

- [ ] **Step 6: Add the real VPN provider**

Create one synchronized singleton:

    object VpnControllerProvider {
        @Volatile
        private var instance: VpnController? = null

        fun create(context: Context): VpnController =
            instance ?: synchronized(this) {
                instance ?: WireGuardVpnController(context.applicationContext)
                    .also { instance = it }
            }
    }

- [ ] **Step 7: Run compile and lint gates**

    .\gradlew.bat testDebugUnitTest lintDebug lintGithubRelease assembleDebug assembleGithubRelease --console=plain

Expected: all tasks succeed; the official WireGuard library manifest supplies GoBackend VpnService only to VPN-capable variants.

### Task 4: Consent, Import, and Minimal VPN UI

**Files:**

- Modify LocationMockScreen.kt to call the build-specific entry point.
- Create src/vpn/java/com/majkeylab/locationmock/VpnFeatureSection.kt.
- Create src/vpn/res/values/strings.xml.

- [ ] **Step 1: Call the build-specific section from the main screen**

Call VpnFeatureSection() where the old Proton section was removed. The Play release resolves this call to the no-op source-set implementation. No VPN strings or controller types enter main sources.

- [ ] **Step 2: Create the real section and controller state**

In the VPN-only composable, create the process singleton once:

    val context = LocalContext.current
    val controller = remember { VpnControllerProvider.create(context) }

Use one LaunchedEffect with a 500 ms delay to copy controller state, error, and hasConfiguration into Compose state. Keep this polling inside the VPN-only source set.

- [ ] **Step 3: Add document import launcher**

Use ActivityResultContracts.OpenDocument with text/plain, application/octet-stream, and application/x-wireguard-profile. Open the returned URI once with contentResolver.openInputStream(uri)?.use and call controller.importConfiguration from a coroutine. Do not retain URI permission because the encrypted copy is app-private.

- [ ] **Step 4: Add VPN consent launcher**

Use ActivityResultContracts.StartActivityForResult. On Connect:

1. Show the prominent disclosure if vpn_disclosure_accepted is false.
2. On affirmative disclosure consent, persist only that boolean.
3. Call controller.permissionIntent.
4. If non-null, launch the Android system VPN dialog.
5. On RESULT_OK, call connect.
6. On denial, remain disconnected and show VPN permission was not granted.

- [ ] **Step 5: Render minimal VPN controls**

VpnSection renders:

- Private network title.
- Clear disclosure summary: traffic is routed to the imported endpoint; Location Mock does not operate the server; traffic is not inspected or logged.
- Import or Replace configuration.
- Remove configuration.
- One primary Connect or Disconnect button.
- State and sanitized error text.

Do not show provider lists, countries, advertisements, throughput graphs, or server recommendations.

- [ ] **Step 6: Add exact English strings in src/vpn/res only**

Add strings for Private network, Import configuration, Replace configuration, Remove configuration, Connect, Disconnect, Connecting, Disconnecting, Connected, Disconnected, disclosure title/body/accept/cancel, import errors, permission denial, and trusted-server requirement. The disclosure body must state that all device traffic matching the imported routes can pass through the remote endpoint.

- [ ] **Step 7: Run UI build and lint gates**

    .\gradlew.bat testDebugUnitTest lintDebug lintGithubRelease assembleDebug assembleGithubRelease bundleRelease --console=plain

Expected: all variants build; the Play release renders no VPN section.

- [ ] **Step 8: Verify denial, invalid import, and removal live**

On emulator:

1. Cancel the prominent disclosure and verify no Android VPN prompt.
2. Accept disclosure, deny Android VPN permission, verify disconnected state.
3. Import malformed config, verify explicit error and no stored replacement.
4. Import a syntactically valid test config, restart the process, verify configuration remains available.
5. Remove it, verify encrypted file deletion and NO_CONFIG.

### Task 5: Documentation, Artifact Isolation, and Physical QA

- [ ] **Step 1: Update privacy and documentation**

Privacy distinguishes builds. Play has address search and no VpnService. GitHub/debug encrypt one imported config, route traffic only through that endpoint, and do not inspect or log traffic. README and changelog explain bring-your-own trusted WireGuard config and no bundled server. Play listing contains no VPN claim.

- [ ] **Step 2: Run clean complete build**

    .\gradlew.bat clean testDebugUnitTest lintDebug lintGithubRelease assembleDebug assembleGithubRelease bundleRelease --console=plain

Expected: all tasks succeed.

- [ ] **Step 3: Verify dependency isolation**

    .\gradlew.bat app:dependencies --configuration releaseRuntimeClasspath --console=plain | Select-String -Pattern "wireguard"
    .\gradlew.bat app:dependencies --configuration githubReleaseRuntimeClasspath --console=plain | Select-String -Pattern "wireguard"

Expected: no release match; one GitHub tunnel 1.0.20260102 match.

- [ ] **Step 4: Verify merged manifests**

Discover generated manifests:

    rg --files app/build/intermediates | rg "merged.*manifest.*AndroidManifest.xml"

Inspect the release manifest and VPN-capable manifests. Release must contain no android.net.VpnService, BIND_VPN_SERVICE, or GoBackend. Debug and githubRelease must contain exported=false GoBackend VpnService protected by BIND_VPN_SERVICE.

- [ ] **Step 5: Physical QA on YAL-L21 only**

Install the signed GitHub package beside Play using adb -s BQLDU19927002646 only. Verify:

1. Address search scenarios from the first plan.
2. Import malformed and valid test configurations.
3. Disclosure and Android consent denial.
4. Configuration remains encrypted after process restart.
5. Connect and disconnect state with a trusted test endpoint if one exists.
6. Play package remains installed and unchanged.

Do not install or test on any other physical device.

- [ ] **Step 6: State the tunnel-verification boundary**

If no trusted WireGuard endpoint is supplied, report successful build, parser, encryption, consent, and failure-path checks. Do not claim successful external traffic routing.

- [ ] **Step 7: Hostile final review**

    git status --short
    git diff --check
    git diff --stat
    rg -n -i "proton|ch\.protonvpn" app README.md CHANGELOG.md docs

Review for configuration leaks, unbounded reads, main-thread blocking, Play VPN leakage, stale Proton copy, invalid states, and unrelated changes. Fix every safe issue.

Expected: no secrets, no Proton matches, no Play VPN leakage, no commit, push, tag, release, PR, or Play Console mutation.
