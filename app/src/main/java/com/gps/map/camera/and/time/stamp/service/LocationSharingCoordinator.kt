package com.gps.map.camera.and.time.stamp.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.gps.map.camera.and.time.stamp.network.TrackerBackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Keeps the local sharing preference and the persistent location service in sync. */
object LocationSharingCoordinator {
    const val PREFS = "tracker_location"
    const val ENABLED = "enabled"
    const val FRIEND_NAME = "friend_name"

    private val backgroundErrorHandler = CoroutineExceptionHandler { _, error ->
        Log.w("LocationSharing", "Background friend sync failed", error)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundErrorHandler)
    private val syncRunning = AtomicBoolean(false)
    private val lastSyncAt = AtomicLong(0L)

    // Background access is requested at most once per app-launch session. This
    // lets Track Friend work with foreground access immediately, then ask again
    // after the next launch without repeatedly interrupting the current flow.
    @Volatile private var deferBackgroundPromptForSession = false

    fun resetPermissionPromptSession() {
        deferBackgroundPromptForSession = false
    }

    fun deferBackgroundPermissionPromptForSession() {
        deferBackgroundPromptForSession = true
    }

    fun shouldDeferBackgroundPermissionPromptForSession(): Boolean =
        deferBackgroundPromptForSession

    fun markAcceptedFriend(context: Context, friendName: String?) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(ENABLED, true)
            .putString(FRIEND_NAME, friendName.orEmpty())
            .apply()
        startIfPermitted(appContext)
    }

    fun startIfPermitted(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!hasRequiredPermissions(appContext)) return false
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, LocationSharingService::class.java)
        )
        return true
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        val foregroundGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!foregroundGranted) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /** Reconciles server-side acceptance with this device after app startup/resume. */
    fun syncAcceptedFriends(context: Context) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastSyncAt.get()
        if (now - previous < SYNC_INTERVAL_MS) return
        if (!syncRunning.compareAndSet(false, true)) return
        lastSyncAt.set(now)
        val appContext = context.applicationContext
        scope.launch {
            try {
                val friends = TrackerBackendClient(appContext).run {
                    register()
                    listFriendsOrLegacy().optJSONArray("friends")
                }
                val names = buildList {
                    if (friends != null) {
                        for (index in 0 until friends.length()) {
                            val friend = friends.optJSONObject(index) ?: continue
                            if (friend.optBoolean("sharing_enabled", false)) {
                                friend.optString("name").ifBlank { friend.optString("device_model") }
                                    .takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    }
                }
                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (names.isNotEmpty()) {
                    prefs.edit()
                        .putBoolean(ENABLED, true)
                        .putString(FRIEND_NAME, names.distinct().take(3).joinToString(", "))
                        .apply()
                    withContext(Dispatchers.Main) { startIfPermitted(appContext) }
                } else if (friends != null && friends.length() == 0) {
                    prefs.edit().putBoolean(ENABLED, false).remove(FRIEND_NAME).apply()
                    withContext(Dispatchers.Main) {
                        appContext.stopService(Intent(appContext, LocationSharingService::class.java))
                    }
                }
            } catch (error: Exception) {
                // This reconciliation runs from app/activity lifecycle callbacks.
                // A temporary backend or quota failure must never escape this
                // background coroutine or show an error outside Track Friend.
                Log.w("LocationSharing", "Friend sync unavailable", error)
            } finally {
                syncRunning.set(false)
            }
        }
    }

    private const val SYNC_INTERVAL_MS = 5 * 60_000L
}
