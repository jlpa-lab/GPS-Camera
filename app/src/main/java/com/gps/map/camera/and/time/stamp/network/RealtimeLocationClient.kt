package com.gps.map.camera.and.time.stamp.network

import android.content.Context
import android.provider.Settings
import com.gps.map.camera.and.time.stamp.ads.RemoteConfigUtils
import com.gps.map.camera.and.time.stamp.data.LocalTrackerDb
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RealtimeLocationClient(
    context: Context,
    private val onFriendshipAccepted: (JSONObject) -> Unit = {},
    private val onBackendUnavailable: (Throwable) -> Unit = {},
    private val onLocation: (JSONObject) -> Unit
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("tracker_backend", Context.MODE_PRIVATE)
    private val deviceId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
    private val socket: Socket by lazy {
        IO.socket(
            RemoteConfigUtils.getBackendBaseUrl().trimEnd('/'),
            IO.Options().apply { transports = arrayOf("websocket"); reconnection = true }
        )
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var authenticated = false
    @Volatile private var pendingLocation: JSONObject? = null

    fun connect() {
        socket.on(Socket.EVENT_CONNECT) {
            socket.emit("authenticate", JSONObject().put("device_id", deviceId).put("app_id", "phone-tracker-0012").put("session_token", prefs.getString("session_token", "")))
        }
        socket.on("authenticated") {
            authenticated = true
            pendingLocation?.let {
                socket.emit("location_update", it)
                pendingLocation = null
            }
        }
        socket.on(Socket.EVENT_DISCONNECT) { authenticated = false }
        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            onBackendUnavailable(
                IllegalStateException(args.firstOrNull()?.toString().orEmpty().ifBlank { "connection failed" })
            )
        }
        socket.on("friendship_accepted") { args ->
            (args.firstOrNull() as? JSONObject)?.let { payload -> onFriendshipAccepted(payload) }
        }
        socket.on("location_updated") { args -> (args.firstOrNull() as? JSONObject)?.let { payload -> LocalTrackerDb(
            appContext
        ).write("friend_location_${payload.optString("device_id")}", payload.toString()); onLocation(payload) } }
        scope.launch {
            val registered = runCatching { TrackerBackendClient(appContext).register(); true }
                .onFailure(onBackendUnavailable)
                .getOrDefault(false)
            if (registered) socket.connect()
        }
    }

    fun sendLocation(latitude: Double, longitude: Double, battery: Float) {
        if (!appContext.getSharedPreferences("tracker_location", Context.MODE_PRIVATE).getBoolean("enabled", false)) return
        val payload = JSONObject().put("latitude", latitude).put("longitude", longitude).put("battery_percentage", battery.coerceIn(0f, 100f))
        if (socket.connected() && authenticated) socket.emit("location_update", payload)
        else pendingLocation = payload
    }

    fun close() { authenticated = false; pendingLocation = null; scope.cancel(); socket.disconnect(); socket.off() }
}
