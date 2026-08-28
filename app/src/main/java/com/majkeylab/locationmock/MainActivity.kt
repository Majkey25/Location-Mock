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
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
            var active by remember { mutableStateOf(preferences.getBoolean(MockLocationService.KEY_ACTIVE, false)) }
            var serviceError by remember { mutableStateOf(preferences.getString(MockLocationService.KEY_ERROR, null)) }
            var actionError by remember { mutableStateOf<String?>(null) }
            var mockAppAllowed by remember { mutableStateOf(isMockLocationApp()) }
            var pendingCoordinates by remember { mutableStateOf<Coordinates?>(null) }

            fun start(coordinates: Coordinates) {
                actionError = null
                serviceError = null
                MockLocationService.start(this, coordinates)
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                val preciseGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                if (preciseGranted) {
                    pendingCoordinates?.let(::start)
                } else {
                    actionError = getString(R.string.error_precise_permission)
                }
                pendingCoordinates = null
            }

            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) mockAppAllowed = isMockLocationApp()
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(preferences) {
                while (true) {
                    active = preferences.getBoolean(MockLocationService.KEY_ACTIVE, false)
                    serviceError = preferences.getString(MockLocationService.KEY_ERROR, null)
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
                    mockAppAllowed = mockAppAllowed,
                    serviceError = actionError ?: serviceError,
                    onStart = { coordinates ->
                        if (!mockAppAllowed) {
                            actionError = getString(R.string.error_select_mock_app)
                            openDeveloperOptions()
                        } else if (
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            start(coordinates)
                        } else {
                            pendingCoordinates = coordinates
                            val permissions = buildList {
                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }.toTypedArray()
                            permissionLauncher.launch(permissions)
                        }
                    },
                    onStop = { MockLocationService.stop(this) },
                    onOpenDeveloperOptions = ::openDeveloperOptions,
                    onOpenVpn = ::openProtonVpn,
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

    private fun openProtonVpn() {
        packageManager.getLaunchIntentForPackage(PROTON_PACKAGE)?.let {
            startActivity(it)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$PROTON_PACKAGE".toUri()))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, PROTON_PLAY_URL.toUri()))
        }
    }

    companion object {
        private const val PROTON_PACKAGE = "ch.protonvpn.android"
        private const val PROTON_PLAY_URL =
            "https://play.google.com/store/apps/details?id=ch.protonvpn.android"
    }
}
