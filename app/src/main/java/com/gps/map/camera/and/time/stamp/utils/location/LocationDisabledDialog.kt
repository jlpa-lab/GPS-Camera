package com.gps.map.camera.and.time.stamp.utils.location

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.Manifest
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.gps.map.camera.and.time.stamp.R

/** Shows the shared location-off popup before opening a location-based feature. */
object LocationDisabledDialog {

    private enum class DialogMode(
        val titleRes: Int,
        val messageRes: Int,
        val primaryRes: Int,
        val secondaryRes: Int,
        val icon: String,
        val opensAppSettings: Boolean,
        val outlinedSecondary: Boolean
    ) {
        DEVICE_LOCATION(
            R.string.location_disabled_dialog_title,
            R.string.location_disabled_dialog_message,
            R.string.location_disabled_dialog_turn_on,
            R.string.location_disabled_dialog_not_now,
            "🗺️",
            opensAppSettings = false,
            outlinedSecondary = false
        ),
        LOCATION_ACCESS(
            R.string.location_access_dialog_title,
            R.string.location_access_dialog_message,
            R.string.location_access_dialog_open_settings,
            R.string.location_access_dialog_continue,
            "⚙️",
            opensAppSettings = true,
            outlinedSecondary = true
        ),
        CAMERA_ACCESS(
            R.string.camera_access_dialog_title,
            R.string.camera_access_dialog_message,
            R.string.camera_access_dialog_open_settings,
            R.string.camera_access_dialog_not_now,
            "⚙️",
            opensAppSettings = true,
            outlinedSecondary = false
        )
    }

    fun showIfLocationDisabled(activity: Activity): Boolean {
        if (!hasLocationPermission(activity)) {
            return showDialog(activity, DialogMode.LOCATION_ACCESS)
        }
        if (isLocationEnabled(activity)) return false
        return showDialog(activity, DialogMode.DEVICE_LOCATION)
    }

    fun showIfCameraAccessDisabled(activity: Activity): Boolean {
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return showDialog(activity, DialogMode.CAMERA_ACCESS)
    }

    private fun showDialog(activity: Activity, mode: DialogMode): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return true

        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_location_disabled)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        val icon = dialog.findViewById<TextView>(R.id.location_dialog_icon)
        val title = dialog.findViewById<TextView>(R.id.location_dialog_title)
        val message = dialog.findViewById<TextView>(R.id.location_dialog_message)
        val primary = dialog.findViewById<TextView>(R.id.location_dialog_turn_on)
        val secondary = dialog.findViewById<TextView>(R.id.location_dialog_not_now)

        icon.text = mode.icon
        title.setText(mode.titleRes)
        message.setText(mode.messageRes)
        primary.setText(mode.primaryRes)
        secondary.setText(mode.secondaryRes)
        if (mode.outlinedSecondary) {
            secondary.setBackgroundResource(R.drawable.bg_location_dialog_secondary)
        }

        dialog.findViewById<TextView>(R.id.location_dialog_turn_on).setOnClickListener {
            dialog.dismiss()
            if (mode.opensAppSettings) {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
            } else {
                activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
        dialog.findViewById<TextView>(R.id.location_dialog_not_now).setOnClickListener {
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply {
                    width = activity.resources.displayMetrics.widthPixels - dp(activity, 48)
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    gravity = Gravity.CENTER
                    dimAmount = 0.72f
                }
            }
        }
        dialog.show()
        dialog.window?.setLayout(
            activity.resources.displayMetrics.widthPixels - dp(activity, 48),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        return true
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
