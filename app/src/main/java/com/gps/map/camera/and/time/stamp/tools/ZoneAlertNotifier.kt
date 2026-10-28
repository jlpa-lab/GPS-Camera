package com.gps.map.camera.and.time.stamp.tools

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gps.map.camera.and.time.stamp.R
import org.json.JSONObject
import kotlin.jvm.java

/** Detects friend entry/exit transitions for local zones and publishes the result. */
object ZoneAlertNotifier {
    data class Event(
        val friendName: String,
        val zone: ZoneRecord,
        val entering: Boolean
    ) {
        fun title(context: Context): String = context.getString(if (entering) R.string.notification_friend_entered else R.string.notification_friend_left, friendName, zone.name)
        fun message(context: Context): String = context.getString(if (entering) R.string.notification_entry_alert else R.string.notification_exit_alert, zone.name)
    }

    fun processFriendLocation(context: Context, payload: JSONObject): Event? {
        val friendId = payload.optString("device_id").trim()
        val latitude = payload.optDouble("latitude", Double.NaN)
        val longitude = payload.optDouble("longitude", Double.NaN)
        if (friendId.isBlank() || latitude.isNaN() || longitude.isNaN()) return null

        val prefs = context.applicationContext.getSharedPreferences(PRESENCE_PREFS, Context.MODE_PRIVATE)
        val friendName = payload.optString("device_model").trim()
            .ifBlank { payload.optString("name").trim() }
            .ifBlank { context.getString(R.string.notification_your_friend) }
        for (zone in ZoneStore.get(context).all()) {
            if (!zone.enabled || (!zone.alertOnEnter && !zone.alertOnExit)) continue
            val distance = FloatArray(1)
            Location.distanceBetween(latitude, longitude, zone.latitude, zone.longitude, distance)
            val inside = distance[0] <= zone.radiusMeters
            val key = "${friendId}_${zone.id}"
            if (!prefs.contains(key)) {
                prefs.edit().putBoolean(key, inside).apply()
                continue
            }
            val wasInside = prefs.getBoolean(key, false)
            if (wasInside == inside) continue
            prefs.edit().putBoolean(key, inside).apply()
            val event = Event(friendName, zone, inside)
            if ((inside && zone.alertOnEnter) || (!inside && zone.alertOnExit)) {
                prefs.edit()
                    .putString(LAST_EVENT_TEXT, "${event.title(context)} · ${event.message(context)}")
                    .putLong(LAST_EVENT_TIME, System.currentTimeMillis())
                    .apply()
                postNotification(context, event, friendId)
                return event
            }
        }
        return null
    }

    private fun postNotification(context: Context, event: Event, friendId: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_zone_alerts), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notificationId = (event.zone.id xor friendId.hashCode().toLong()).toInt()
//        val pendingIntent = PendingIntent.getActivity(
//            context, notificationId,
//            Intent(context, ZoneAlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
//            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
//        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location_pin_white)
            .setColor(if (event.zone.safe) Color.rgb(0, 164, 76) else Color.rgb(229, 74, 70))
            .setContentTitle(event.title(context))
            .setContentText(event.message(context))
         //   .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId, notification)
    }

    fun latestMessage(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PRESENCE_PREFS, Context.MODE_PRIVATE)
        val time = prefs.getLong(LAST_EVENT_TIME, 0L)
        if (time == 0L || System.currentTimeMillis() - time > LAST_EVENT_LIFETIME_MS) return null
        return prefs.getString(LAST_EVENT_TEXT, null)
    }

    const val CHANNEL_ID = "saved_zone_alerts"
    private const val PRESENCE_PREFS = "zone_friend_presence"
    private const val LAST_EVENT_TEXT = "last_event_text"
    private const val LAST_EVENT_TIME = "last_event_time"
    private const val LAST_EVENT_LIFETIME_MS = 10 * 60 * 1000L
}
