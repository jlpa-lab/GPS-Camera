package com.gps.map.camera.and.time.stamp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LocationSharingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED && intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        try {
            val enabled = context.getSharedPreferences(LocationSharingCoordinator.PREFS, Context.MODE_PRIVATE)
                .getBoolean(LocationSharingCoordinator.ENABLED, false)
            if (enabled) LocationSharingCoordinator.startIfPermitted(context)
        } finally {
            pendingResult.finish()
        }
    }
}
