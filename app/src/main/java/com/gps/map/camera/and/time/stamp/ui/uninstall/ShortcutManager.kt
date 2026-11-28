package com.gps.map.camera.and.time.stamp.ui.uninstall

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import com.ads.module.ads.ERainAd
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.RemoteConfigUtils
import com.gps.map.camera.and.time.stamp.app.AppConstants
import com.gps.map.camera.and.time.stamp.bases.ext.getSystemLocaleString
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthViewActivity
import com.gps.map.camera.and.time.stamp.ui.camera.CameraActivity
import com.gps.map.camera.and.time.stamp.ui.splash.SplashActivity

object ShortcutManager {
    /** Sync launcher shortcuts with the remote-configured uninstall widget flag. */
    fun initIfEligible(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return

        if (!RemoteConfigUtils.getOnEnableUninstallWidget()) {
            removeShortcuts(context)
            return
        }

        // The SDK helper argument is not the Remote Config flag. Passing false
        // is the reference app's normal eligibility check.
        if (ERainAd.getInstance().getShouldDisplayWidgetUninstall(false)) {
            initShortCut(context)
        } else {
            removeShortcuts(context)
        }
    }

    fun initShortCut(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        try {
            val uninstallShortcut = ShortcutInfo.Builder(context, AppConstants.ACTION_OPEN_UNINSTALL)
                .setShortLabel(context.getSystemLocaleString(R.string.txt_uninstall))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_uninstall))
                .setIntent(Intent(context, SplashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    action = "android.intent.action.SHORTCUT_UNINSTALL_APP"
                    putExtra(AppConstants.FROM_SHORTCUT, AppConstants.ACTION_OPEN_UNINSTALL)
                })
                .setRank(1)
                .build()
            val gpsCameraShortcut = ShortcutInfo.Builder(context, AppConstants.ACTION_OPEN_GPS_CAMERA)
                .setShortLabel(context.getString(R.string.shortcut_gps_camera_short))
                .setLongLabel(context.getString(R.string.shortcut_gps_camera_long))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_tool_camera))
                .setIntent(Intent(context, CameraActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(AppConstants.FROM_SHORTCUT, AppConstants.ACTION_OPEN_GPS_CAMERA)
                })
                .setRank(2)
                .build()
            val earthViewShortcut = ShortcutInfo.Builder(context, AppConstants.ACTION_OPEN_EARTH_VIEW)
                .setShortLabel(context.getString(R.string.shortcut_earth_view_short))
                .setLongLabel(context.getString(R.string.shortcut_earth_view_long))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_globe))
                .setIntent(Intent(context, EarthViewActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(AppConstants.FROM_SHORTCUT, AppConstants.ACTION_OPEN_EARTH_VIEW)
                })
                .setRank(3)
                .build()
            manager.dynamicShortcuts = listOf(uninstallShortcut, gpsCameraShortcut, earthViewShortcut)
        } catch (error: Exception) {
            Log.e("ShortcutManager", "Unable to publish launcher shortcuts", error)
        }
    }

    private fun removeShortcuts(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        try {
            manager.removeAllDynamicShortcuts()
        } catch (error: Exception) {
            Log.e("ShortcutManager", "Unable to remove launcher shortcuts", error)
        }
    }
}
