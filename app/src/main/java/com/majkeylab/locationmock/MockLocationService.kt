package com.majkeylab.locationmock

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MockLocationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationClient: FusedLocationProviderClient
    private var coordinates: Coordinates? = null
    private var stopping = false
    private var generation = 0

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMocking(error = null)
            return START_NOT_STICKY
        }
        val requested = runCatching {
            Coordinates.of(
                intent?.getDoubleExtra(EXTRA_LATITUDE, Double.NaN) ?: Double.NaN,
                intent?.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN) ?: Double.NaN,
            )
        }.getOrElse {
            setState(active = false, error = it.message ?: getString(R.string.error_mock_not_available))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setState(active = false, error = getString(R.string.error_location_permission))
            stopSelf(startId)
            return START_NOT_STICKY
        }

        stopping = false
        coordinates = requested
        val currentGeneration = ++generation
        handler.removeCallbacksAndMessages(null)
        try {
            startForeground(
                NOTIFICATION_ID,
                notification(getString(R.string.notification_starting)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (cause: SecurityException) {
            setState(active = false, error = cause.message ?: getString(R.string.error_location_permission))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        locationClient.setMockMode(true)
            .addOnSuccessListener {
                if (!stopping && currentGeneration == generation) publishLocation(currentGeneration)
            }
            .addOnFailureListener { cause ->
                if (currentGeneration == generation) fail(cause)
            }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        generation++
        handler.removeCallbacksAndMessages(null)
        coordinates = null
        isRunning = false
        isStopping = false
        if (!stopping) {
            try {
                locationClient.setMockMode(false)
            } catch (_: SecurityException) {
                // Permission may be revoked while the service is running.
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publishLocation(requestGeneration: Int) {
        if (stopping || requestGeneration != generation) return
        val current = coordinates ?: return
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            fail(SecurityException(getString(R.string.error_location_permission)))
            return
        }
        locationClient.setMockLocation(current.toLocation())
            .addOnSuccessListener {
                if (!stopping && requestGeneration == generation) {
                    setState(active = true, error = null)
                    updateNotification(current)
                    handler.postDelayed(
                        { publishLocation(requestGeneration) },
                        UPDATE_INTERVAL_MS,
                    )
                }
            }
            .addOnFailureListener { cause ->
                if (requestGeneration == generation) fail(cause)
            }
    }

    private fun Coordinates.toLocation(): Location = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = this@toLocation.latitude
        longitude = this@toLocation.longitude
        altitude = 0.0
        accuracy = 3f
        speed = 0f
        bearing = 0f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        verticalAccuracyMeters = 3f
        speedAccuracyMetersPerSecond = 0.1f
        bearingAccuracyDegrees = 1f
    }

    private fun fail(cause: Throwable) {
        if (stopping) return
        stopMocking(cause.message ?: getString(R.string.error_mock_not_available))
    }

    private fun stopMocking(error: String?) {
        if (stopping) return
        stopping = true
        isStopping = true
        val stopGeneration = ++generation
        handler.removeCallbacksAndMessages(null)
        coordinates = null

        val finish = finish@{
            if (stopGeneration != generation) return@finish
            isStopping = false
            setState(active = false, error = error)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        try {
            locationClient.setMockMode(false).addOnCompleteListener { finish() }
        } catch (_: SecurityException) {
            finish()
        }
    }

    private fun setState(active: Boolean, error: String?) {
        isRunning = active
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit {
            putString(KEY_ERROR, error)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(current: Coordinates) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notification(
                getString(
                    R.string.notification_active,
                    current.latitude,
                    current.longitude,
                ),
            ),
        )
    }

    private fun notification(text: String): Notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            Notification.Action.Builder(
                null,
                getString(R.string.notification_stop),
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build(),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    companion object {
        const val PREFERENCES = "location_mock"
        const val KEY_ERROR = "error"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isStopping: Boolean = false
            private set

        private const val EXTRA_LATITUDE = "latitude"
        private const val EXTRA_LONGITUDE = "longitude"
        private const val ACTION_STOP = "com.majkeylab.locationmock.STOP"
        private const val NOTIFICATION_CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 1_000L

        fun start(context: Context, coordinates: Coordinates) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
                putString(KEY_LATITUDE, coordinates.latitude.toString())
                putString(KEY_LONGITUDE, coordinates.longitude.toString())
                putString(KEY_ERROR, null)
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, MockLocationService::class.java)
                    .putExtra(EXTRA_LATITUDE, coordinates.latitude)
                    .putExtra(EXTRA_LONGITUDE, coordinates.longitude),
            )
        }

        fun stop(context: Context) {
            isStopping = true
            try {
                context.startService(
                    Intent(context, MockLocationService::class.java).setAction(ACTION_STOP),
                )
            } catch (cause: RuntimeException) {
                isStopping = false
                throw cause
            }
        }
    }
}
