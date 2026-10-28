package com.gps.map.camera.and.time.stamp.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.network.RealtimeLocationClient
import com.gps.map.camera.and.time.stamp.tools.ZoneAlertNotifier
import com.gps.map.camera.and.time.stamp.tools.ZoneStore
import org.json.JSONObject

class LocationSharingService : Service() {
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var realtime: RealtimeLocationClient
    private var callback: LocationCallback? = null
    private var sharingEnabled = false
    private var lastSentLocation: Location? = null
    private var lastSentAtElapsedMs = 0L
    private val locationHandler = Handler(Looper.getMainLooper())
    private val locationHeartbeat = object : Runnable {
        override fun run() {
            if (!sharingEnabled || !::fused.isInitialized || !::realtime.isInitialized) return
            requestCurrentLocation()
            locationHandler.postDelayed(this, LOCATION_SAMPLE_INTERVAL_MS)
        }
    }
    override fun onCreate() {
        super.onCreate()
        sharingEnabled = getSharedPreferences(LocationSharingCoordinator.PREFS, MODE_PRIVATE)
            .getBoolean(LocationSharingCoordinator.ENABLED, false)
        val monitorZones = ZoneStore.get(this).all().any { it.enabled && (it.alertOnEnter || it.alertOnExit) }
        createChannel()
        startForeground(NOTIFICATION_ID, notification(sharingEnabled))
        if (!sharingEnabled && !monitorZones) { stopSelf(); return }
        fused = LocationServices.getFusedLocationProviderClient(this)
        realtime = RealtimeLocationClient(
            this,
            onLocation = { payload -> ZoneAlertNotifier.processFriendLocation(this, payload) },
            onFriendshipAccepted = { payload -> handleFriendshipAccepted(payload) }
        )
        realtime.connect()
        if (sharingEnabled) startSharingUpdates()
    }
    private fun batteryPercentage(): Float = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { it.getIntExtra("level", 0) * 100f / it.getIntExtra("scale", 100).coerceAtLeast(1) } ?: 0f
    private fun createChannel() { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notification_location_sharing), NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(sharing: Boolean): Notification {
        val friendName = getSharedPreferences(LocationSharingCoordinator.PREFS, MODE_PRIVATE)
            .getString(LocationSharingCoordinator.FRIEND_NAME, "")
            .orEmpty()
        val content = if (sharing && friendName.isNotBlank()) {
            getString(R.string.notification_location_shared_with, friendName)
        } else {
            getString(if (sharing) R.string.notification_location_shared_text else R.string.notification_watching_zones)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(if (sharing) R.string.notification_location_shared_title else R.string.notification_zones_active))
            .setContentText(content)
            .setOngoing(true)
            .build()
    }

    private fun handleFriendshipAccepted(payload: JSONObject) {
        val friendName = payload.optString("friend_name").ifBlank { payload.optString("name") }
        getSharedPreferences(LocationSharingCoordinator.PREFS, MODE_PRIVATE).edit()
            .putBoolean(LocationSharingCoordinator.ENABLED, true)
            .putString(LocationSharingCoordinator.FRIEND_NAME, friendName)
            .apply()
        sharingEnabled = true
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(true))
        Handler(Looper.getMainLooper()).post { startSharingUpdates() }
    }
    private fun startSharingUpdates() {
        if (!::fused.isInitialized || !::realtime.isInitialized) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        // Always send one fresh location when sharing starts or is re-enabled.
        lastSentLocation = null
        lastSentAtElapsedMs = 0L
        callback?.let { fused.removeLocationUpdates(it) }
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::handleLocationUpdate)
            }
        }

        fused.requestLocationUpdates(
            // Sample once per minute so a 100 m movement can be detected while
            // uploads remain throttled by handleLocationUpdate().
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_SAMPLE_INTERVAL_MS
            )
                .setMinUpdateIntervalMillis(LOCATION_SAMPLE_INTERVAL_MS)
                .setMinUpdateDistanceMeters(0f)
                .build(),
            callback!!,
            Looper.getMainLooper()
        )
        requestCurrentLocation()
        locationHandler.removeCallbacks(locationHeartbeat)
        locationHandler.postDelayed(locationHeartbeat, LOCATION_SAMPLE_INTERVAL_MS)
    }

    private fun handleLocationUpdate(location: Location) {
        if (!sharingEnabled || !::realtime.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        val distanceFromLastSent = lastSentLocation?.distanceTo(location)
        val stationaryIntervalElapsed = lastSentAtElapsedMs == 0L ||
            now - lastSentAtElapsedMs >= STATIONARY_UPLOAD_INTERVAL_MS
        val movedFarEnough = distanceFromLastSent == null ||
            distanceFromLastSent >= MOVEMENT_THRESHOLD_METERS

        // Upload immediately after a 100 m movement. If the device has not
        // moved that far, upload only once every 15 minutes.
        if (!movedFarEnough && !stationaryIntervalElapsed) return

        lastSentLocation = Location(location)
        lastSentAtElapsedMs = now
        realtime.sendLocation(location.latitude, location.longitude, batteryPercentage())
    }

    private fun requestCurrentLocation() {
        if (!::fused.isInitialized || !::realtime.isInitialized) return
        runCatching {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    location?.let(::handleLocationUpdate)
                }
                .addOnFailureListener {
                    fused.lastLocation.addOnSuccessListener { location ->
                        location?.let(::handleLocationUpdate)
                    }
                }
        }
    }

    override fun onDestroy() {
        locationHandler.removeCallbacks(locationHeartbeat)
        callback?.let { if (::fused.isInitialized) fused.removeLocationUpdates(it) }
        if (::realtime.isInitialized) realtime.close()
        super.onDestroy()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedSharing = getSharedPreferences(LocationSharingCoordinator.PREFS, MODE_PRIVATE)
            .getBoolean(LocationSharingCoordinator.ENABLED, false)
        if (requestedSharing != sharingEnabled) {
            sharingEnabled = requestedSharing
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(sharingEnabled))
            if (sharingEnabled) startSharingUpdates() else {
                locationHandler.removeCallbacks(locationHeartbeat)
                callback?.let { if (::fused.isInitialized) fused.removeLocationUpdates(it) }
                callback = null
                lastSentLocation = null
                lastSentAtElapsedMs = 0L
            }
        }
        val monitorZones = ZoneStore.get(this).all().any { it.enabled && (it.alertOnEnter || it.alertOnExit) }
        if (!sharingEnabled && !monitorZones) stopSelf()
        if (sharingEnabled && ::fused.isInitialized && ::realtime.isInitialized) requestCurrentLocation()
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL = "location_sharing"
        private const val NOTIFICATION_ID = 1001
        private const val LOCATION_SAMPLE_INTERVAL_MS = 60_000L
        private const val STATIONARY_UPLOAD_INTERVAL_MS = 15 * 60_000L
        private const val MOVEMENT_THRESHOLD_METERS = 100f
    }
}
