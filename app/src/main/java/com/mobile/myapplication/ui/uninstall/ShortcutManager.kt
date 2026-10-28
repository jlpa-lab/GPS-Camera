package com.mobile.myapplication.ui.uninstall

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import com.mobile.myapplication.R
import com.mobile.myapplication.app.AppConstants
import com.mobile.myapplication.bases.ext.getSystemLocaleString
import com.mobile.myapplication.ui.splash.SplashActivity
import com.mobile.myapplication.ui.welcome.WelcomeActivity


object ShortcutManager {
    fun initShortCut(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val manager = context.getSystemService(ShortcutManager::class.java)
            try {
                manager.removeAllDynamicShortcuts()
                val uninstallShortCut = ShortcutInfo.Builder(context, AppConstants.ACTION_OPEN_UNINSTALL)
                    .setShortLabel(context.
                    getSystemLocaleString(R.string.txt_uninstall))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_uninstall))
                    .setIntent(Intent(context, SplashActivity::class.java).apply {
                        flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        action = "android.intent.action.SHORTCUT_UNINSTALL_APP"
                        putExtra(
                            AppConstants.FROM_SHORTCUT,
                            AppConstants.ACTION_OPEN_UNINSTALL
                        )
                    })
                    .setRank(1)
                    .build()
                val phoneLocator = ShortcutInfo.Builder(context, "app_feature_1")
                    .setShortLabel(context.getString(R.string.shortcut_phone_locator_short))
                    .setLongLabel(context.getString(R.string.shortcut_phone_locator_long))
                    .setIcon(Icon.createWithResource(context, R.drawable.bg_circle_app_color))
                    .setIntent(Intent(context, WelcomeActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(AppConstants.FROM_SHORTCUT, AppConstants.ACTION_OPEN_PHONE_LOCATOR)
                       // putExtra(AppConstants.EXTRA_SHORTCUT_DESTINATION, PhoneLocatorActivity::class.java.name)
                    })
                    .setRank(2)
                    .build()
                val trackFriend = ShortcutInfo.Builder(context, "app_feature_2")
                    .setShortLabel(context.getString(R.string.shortcut_track_friend_short))
                    .setLongLabel(context.getString(R.string.shortcut_track_friend_long))
                    .setIcon(Icon.createWithResource(context, R.drawable.bg_circle_app_color))
                    .setIntent(Intent(context, WelcomeActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(AppConstants.FROM_SHORTCUT, AppConstants.ACTION_OPEN_TRACK_FRIEND)
                       // putExtra(AppConstants.EXTRA_SHORTCUT_DESTINATION, TrackFriendActivity::class.java.name)
                    })
                    .setRank(3)
                    .build()
                // Same simple registration pattern as the reference app.
                manager.dynamicShortcuts = listOf(uninstallShortCut, phoneLocator, trackFriend)
            } catch (error: Exception) {
                Log.e("ShortcutManager", "Unable to publish launcher shortcuts", error)
            }
        }
    }
}








