# Address Search and Proton Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Add Android address lookup to every build and remove the complete Proton VPN handoff without changing mock-location behavior.

**Architecture:** AddressSearchRepository validates bounded text queries and delegates to Android Geocoder. The Compose screen owns transient query/result state, calls the repository from a coroutine, and copies a selected result into the existing coordinate fields.

**Tech Stack:** Kotlin 2.3.21, Android SDK 29-37, Android Geocoder, coroutines, Jetpack Compose Material 3, JUnit 4.

**Spec:** docs/superpowers/specs/2026-08-29-location-search-wireguard-design.md

## Global Constraints

- Keep Play application ID com.majkeylab.locationmock.
- Keep GitHub application ID com.majkeylab.locationmock.github.
- Preserve the mock-location foreground service and direct coordinate flow.
- Return at most five geocoder results.
- Trim queries and require 3-200 characters.
- Never block the main thread.
- Add no map SDK or geocoding dependency.
- Remove every Proton package, string, UI, privacy, and listing reference.
- Set version 1.1.0-rc.1 and code 3.
- Do not commit, tag, push, publish, or modify Play Console.

---

## File Structure

- Create app/src/main/java/com/majkeylab/locationmock/AddressSearch.kt: result type, query validation, Android geocoder adapter.
- Create app/src/test/java/com/majkeylab/locationmock/AddressQueryTest.kt: validation coverage.
- Modify MainActivity.kt: create and pass the repository.
- Modify LocationMockScreen.kt: address controls and result selection.
- Modify AndroidManifest.xml and strings.xml: remove Proton and add address copy.
- Modify app/build.gradle.kts, README.md, CHANGELOG.md, docs/privacy.html, and docs/play-store/listing.md.

### Task 1: Address Boundary and Geocoder Adapter

**Interfaces:**

- Produces AddressQuery.parse(raw: String): AddressQuery.
- Produces AddressSearchResult(label: String, coordinates: Coordinates).
- Produces suspend AddressSearchRepository.search(rawQuery: String): List<AddressSearchResult>.

- [ ] **Step 1: Write the failing validation test**

    class AddressQueryTest {
        @Test
        fun trimsValidQuery() {
            assertEquals("Gorilla Hall Osaka", AddressQuery.parse("  Gorilla Hall Osaka  ").value)
        }

        @Test
        fun rejectsTooShortQuery() {
            assertEquals(
                "Enter at least 3 characters.",
                assertFailsWith<IllegalArgumentException> { AddressQuery.parse("ab") }.message,
            )
        }

        @Test
        fun rejectsTooLongQuery() {
            assertEquals(
                "Address must be 200 characters or fewer.",
                assertFailsWith<IllegalArgumentException> {
                    AddressQuery.parse("a".repeat(201))
                }.message,
            )
        }
    }

- [ ] **Step 2: Run the focused test and confirm failure**

    .\gradlew.bat testDebugUnitTest --tests com.majkeylab.locationmock.AddressQueryTest --console=plain

Expected: compilation fails because AddressQuery does not exist.

- [ ] **Step 3: Implement the pure types**

    @JvmInline
    value class AddressQuery private constructor(val value: String) {
        companion object {
            fun parse(raw: String): AddressQuery {
                val value = raw.trim()
                require(value.length >= 3) { "Enter at least 3 characters." }
                require(value.length <= 200) { "Address must be 200 characters or fewer." }
                return AddressQuery(value)
            }
        }
    }

    data class AddressSearchResult(
        val label: String,
        val coordinates: Coordinates,
    )

- [ ] **Step 4: Implement AddressSearchRepository**

Use Geocoder.isPresent before searching. On API 33+, wrap getFromLocationName(query, 5, GeocodeListener) with suspendCancellableCoroutine. On API 29-32, call the legacy overload only inside withContext(Dispatchers.IO).

    class AddressSearchRepository(context: Context) {
        private val geocoder = Geocoder(context.applicationContext, Locale.getDefault())

        suspend fun search(rawQuery: String): List<AddressSearchResult> {
            val query = AddressQuery.parse(rawQuery)
            check(Geocoder.isPresent()) {
                "Address search is unavailable on this device."
            }
            return queryAddresses(query.value)
                .mapNotNull(::toResult)
                .distinctBy {
                    Triple(it.label, it.coordinates.latitude, it.coordinates.longitude)
                }
                .take(MAX_RESULTS)
        }
    }

toResult uses address line 0, then feature/locality/country parts, then numeric coordinates. It rejects non-finite or out-of-range coordinates through Coordinates.of. Empty results are returned as an empty list.

- [ ] **Step 5: Run all unit tests**

    .\gradlew.bat testDebugUnitTest --console=plain

Expected: five coordinate tests plus three address-query tests pass.

- [ ] **Step 6: Review without committing**

    git diff --check
    git diff -- app/src/main/java/com/majkeylab/locationmock/AddressSearch.kt app/src/test/java/com/majkeylab/locationmock/AddressQueryTest.kt

### Task 2: Address UI and Proton Removal

**Interfaces:**

- Consumes suspend AddressSearchRepository.search(rawQuery: String).
- Adds onAddressSearch: suspend (String) -> List<AddressSearchResult> to LocationMockScreen.

- [ ] **Step 1: Wire AddressSearchRepository from MainActivity**

Create it once with remember and pass addressSearchRepository::search. Delete openProtonVpn, Proton constants, Proton imports, and onOpenVpn wiring.

    val addressSearchRepository = remember { AddressSearchRepository(this) }

    LocationMockScreen(
        // existing arguments
        onAddressSearch = addressSearchRepository::search,
    )

- [ ] **Step 2: Add bounded Compose state**

    var addressQuery by rememberSaveable { mutableStateOf("") }
    var addressResults by remember {
        mutableStateOf(emptyList<AddressSearchResult>())
    }
    var addressError by remember { mutableStateOf<String?>(null) }
    var addressSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

Search clears stale state, runs one coroutine, shows No addresses found for an empty success, and displays the exception message for validation or geocoder failures.

- [ ] **Step 3: Add minimal controls above coordinate fields**

Add one single-line OutlinedTextField, one full-width OutlinedButton, one small loading indicator, address_help text, and up to five simple TextButton result rows. Selecting a row copies latitude and longitude into the existing fields and clears results. Disable search and coordinate editing while mock location is active, starting, stopping, or searching.

Do not add cards, maps, autocomplete, history, or caching.

- [ ] **Step 4: Remove Proton manifest and copy**

Delete the manifest queries block. Delete ip_title, ip_body, open_proton, and vpn_disclaimer. Keep pricing_note. Add:

    <string name="address_search_label">Search address or place</string>
    <string name="address_search_action">Search</string>
    <string name="address_searching">Searching…</string>
    <string name="address_no_results">No addresses found.</string>
    <string name="address_help">Results come from the device geocoding service and may be unavailable or inaccurate.</string>

- [ ] **Step 5: Run task gates**

    .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain

Expected: all tasks succeed with warnings-as-errors.

- [ ] **Step 6: Review without committing**

    git diff --check
    rg -n -i "proton|ch\.protonvpn" app

Expected: no Proton matches in app source.

### Task 3: Version, Documentation, and Live Address QA

- [ ] **Step 1: Bump release metadata**

Set versionCode = 3 and versionName = "1.1.0-rc.1". Update the About dialog version text.

- [ ] **Step 2: Update documentation**

README and listing describe address lookup, direct coordinates, and presets. Privacy says address text may be processed by the Android device geocoding service, is not sent to a maintainer server, and is not stored as history. Remove all Proton references.

- [ ] **Step 3: Prove Proton removal**

    rg -n -i "proton|ch\.protonvpn" app README.md CHANGELOG.md docs

Expected: no matches.

- [ ] **Step 4: Run the complete pre-VPN build gate**

    .\gradlew.bat testDebugUnitTest lintDebug lintGithubRelease assembleDebug assembleGithubRelease bundleRelease --console=plain

Expected: all tasks succeed.

- [ ] **Step 5: Live-test four address scenarios**

On emulator, then authorized YAL-L21 with every physical-device command pinned to adb -s BQLDU19927002646:

1. Gorilla Hall Osaka returns a selectable Osaka result and fills valid coordinates.
2. London returns multiple results without auto-selection.
3. ab shows Enter at least 3 characters without calling Geocoder.
4. Prague preset and direct numeric entry still start and stop mock location.

If the device geocoder is unavailable or empty, retain the explicit failure UI and report the exact result. Do not add a hidden network API.

- [ ] **Step 6: Final pre-VPN review**

    git status --short
    git diff --check
    git diff --stat

Expected: only approved files and design/plan documents changed. No commit, push, tag, release, or Play Console mutation.
