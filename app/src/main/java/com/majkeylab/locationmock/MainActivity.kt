package com.majkeylab.locationmock

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences = remember {
                getSharedPreferences(MockLocationService.PREFERENCES, MODE_PRIVATE)
            }
            val addressSearchRepository = remember { AddressSearchRepository(this) }
            var active by remember { mutableStateOf(MockLocationService.isRunning) }
            var starting by rememberSaveable { mutableStateOf(false) }
            var stopping by remember { mutableStateOf(MockLocationService.isStopping) }
            var serviceError by remember { mutableStateOf(preferences.getString(MockLocationService.KEY_ERROR, null)) }
            var actionError by remember { mutableStateOf<String?>(null) }
            var mockAppAllowed by remember { mutableStateOf(isMockLocationApp()) }
            var pendingLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
            var pendingLongitude by rememberSaveable { mutableStateOf<Double?>(null) }

            fun start(coordinates: Coordinates) {
                actionError = null
                serviceError = null
                starting = true
                MockLocationService.start(this, coordinates)
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                val locationGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                if (locationGranted) {
                    val latitude = pendingLatitude
                    val longitude = pendingLongitude
                    if (latitude != null && longitude != null) {
                        start(Coordinates.of(latitude, longitude))
                    }
                } else {
                    actionError = getString(R.string.error_location_permission)
                }
                pendingLatitude = null
                pendingLongitude = null
            }

            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        val allowed = isMockLocationApp()
                        mockAppAllowed = allowed
                        if (allowed && actionError == getString(R.string.error_select_mock_app)) {
                            actionError = null
                        }
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(preferences) {
                while (true) {
                    active = MockLocationService.isRunning
                    stopping = MockLocationService.isStopping
                    val latestError = preferences.getString(MockLocationService.KEY_ERROR, null)
                    serviceError = latestError
                    if (stopping) starting = false
                    if (active || latestError != null) starting = false
                    delay(500)
                }
            }

            LocationMockTheme {
                LocationMockScreen(
                    initialLatitude = preferences.getString(MockLocationService.KEY_LATITUDE, "50.0755")
                        ?: "50.0755",
                    initialLongitude = preferences.getString(MockLocationService.KEY_LONGITUDE, "14.4378")
                        ?: "14.4378",
                    active = active,
                    starting = starting,
                    stopping = stopping,
                    mockAppAllowed = mockAppAllowed,
                    serviceError = actionError ?: serviceError,
                    onStart = { coordinates ->
                        if (!mockAppAllowed) {
                            actionError = getString(R.string.error_select_mock_app)
                            openDeveloperOptions()
                        } else {
                            val permissions = buildList {
                                if (
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                                if (
                                    android.os.Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            if (permissions.isEmpty()) {
                                start(coordinates)
                            } else {
                                pendingLatitude = coordinates.latitude
                                pendingLongitude = coordinates.longitude
                                permissionLauncher.launch(permissions.toTypedArray())
                            }
                        }
                    },
                    onStop = {
                        starting = false
                        stopping = true
                        MockLocationService.stop(this)
                    },
                    onAddressSearch = addressSearchRepository::search,
                    onOpenDeveloperOptions = ::openDeveloperOptions,
                )
            }
        }
    }

    private fun isMockLocationApp(): Boolean = runCatching {
        getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun openDeveloperOptions() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
