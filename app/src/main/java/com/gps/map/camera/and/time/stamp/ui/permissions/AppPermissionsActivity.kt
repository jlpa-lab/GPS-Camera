package com.gps.map.camera.and.time.stamp.ui.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.databinding.ActivityAppPermissionsBinding
import com.gps.map.camera.and.time.stamp.ui.BaseActivity
import com.gps.map.camera.and.time.stamp.utils.Routes

class AppPermissionsActivity : BaseActivity<ActivityAppPermissionsBinding>() {

    companion object {
        const val EXTRA_FROM_ONBOARDING = "extra_from_onboarding"
    }

    private enum class PermissionKind(
        val rowId: Int,
        val titleRes: Int,
        val descriptionRes: Int,
        val iconRes: Int,
        val permissions: () -> Array<String>
    ) {
        CAMERA(
            R.id.permission_camera,
            R.string.permissions_camera_title,
            R.string.permissions_camera_description,
            R.drawable.permissions_camera,
            { arrayOf(Manifest.permission.CAMERA) }
        ),
        LOCATION(
            R.id.permission_location,
            R.string.permissions_location_title,
            R.string.permissions_location_description,
            R.drawable.permissions_location,
            {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        ),
        PHOTOS(
            R.id.permission_photos,
            R.string.permissions_photos_title,
            R.string.permissions_photos_description,
            R.drawable.permissions_photos,
            {
                arrayOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                )
            }
        ),
        MICROPHONE(
            R.id.permission_microphone,
            R.string.permissions_microphone_title,
            R.string.permissions_microphone_description,
            R.drawable.permissions_microphone,
            { arrayOf(Manifest.permission.RECORD_AUDIO) }
        )
    }

    private val fromOnboarding: Boolean
        get() = intent.getBooleanExtra(EXTRA_FROM_ONBOARDING, false)

    private val permissionPreferences by lazy {
        getSharedPreferences("app_permissions", MODE_PRIVATE)
    }

    private var syncingPermissionSwitches = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissionRows()
        if (fromOnboarding && allPermissionsGranted()) {
            finishPermissionFlow()
        } else if (!fromOnboarding && allPermissionsGranted()) {
            finish()
        }
    }

    override fun getLayoutActivity(): Int = R.layout.activity_app_permissions

    override fun initViews() {
        if (fromOnboarding && allPermissionsGranted()) {
            finishPermissionFlow()
            return
        }

        PermissionKind.entries.forEach { kind ->
            val rowRoot = findViewById<View>(kind.rowId)
            rowRoot.findViewById<ImageView>(R.id.permission_icon).setImageResource(kind.iconRes)
            rowRoot.findViewById<TextView>(R.id.permission_name).setText(kind.titleRes)
            rowRoot.findViewById<TextView>(R.id.permission_description).setText(kind.descriptionRes)
            rowRoot.findViewById<View>(R.id.permission_card).setOnClickListener { onPermissionSelected(kind) }
            rowRoot.findViewById<SwitchCompat>(R.id.permission_switch).setOnCheckedChangeListener { button, checked ->
                if (syncingPermissionSwitches) return@setOnCheckedChangeListener

                if (checked) {
                    onPermissionSelected(kind)
                } else {
                    // Runtime permissions cannot be revoked by the app. Restore the
                    // switch immediately and provide the platform settings path.
                    syncingPermissionSwitches = true
                    button.isChecked = isPermissionGranted(kind)
                    syncingPermissionSwitches = false
                    if (isPermissionGranted(kind)) showPermissionSettingsDialog(kind)
                }
            }
        }

        mBinding.permissionsStart.setOnClickListener {
            if (allPermissionsGranted()) finishPermissionFlow()
        }
        mBinding.permissionsSettingsHint.setOnClickListener {
            Routes.startMainActivity(this)
            finish()
        }
        refreshPermissionRows()
    }

    override fun onResume() {
        super.onResume()
        if (::mBinding.isInitialized) refreshPermissionRows()
    }

    override fun onActivityBackPressed() {
        if (!fromOnboarding) finish()
    }

    private fun onPermissionSelected(kind: PermissionKind) {
        if (isPermissionGranted(kind)) {
            // Android does not allow an app to revoke its own runtime permission.
            // Explain the platform behavior instead of leaving a tap with no result.
            refreshPermissionRows()
            showPermissionSettingsDialog(kind)
            return
        }

        val missing = kind.permissions().filterNot(::hasPermission)
        val requestedBefore = permissionPreferences.getBoolean(kind.name, false)
        val canShowSystemPrompt = missing.any {
            ActivityCompat.shouldShowRequestPermissionRationale(this, it)
        }

        if (requestedBefore && missing.isNotEmpty() && !canShowSystemPrompt) {
            // A permanently denied permission cannot be requested again from the app.
            // Give the user a direct, visible recovery path.
            showPermissionSettingsDialog(kind)
            return
        }

        permissionPreferences.edit().putBoolean(kind.name, true).apply()
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun refreshPermissionRows() {
        syncingPermissionSwitches = true
        try {
            PermissionKind.entries.forEach { kind ->
                findViewById<View>(kind.rowId)
                    .findViewById<SwitchCompat>(R.id.permission_switch)
                    .isChecked = isPermissionGranted(kind)
            }
        } finally {
            syncingPermissionSwitches = false
        }
        val ready = allPermissionsGranted()
        mBinding.permissionsStart.isEnabled = ready
        mBinding.permissionsStart.alpha = if (ready) 1f else 0.92f
    }

    private fun allPermissionsGranted(): Boolean =
        PermissionKind.entries.all(::isPermissionGranted)

    private fun isPermissionGranted(kind: PermissionKind): Boolean {
        val permissions = kind.permissions()
        return if (kind == PermissionKind.LOCATION) {
            permissions.any(::hasPermission)
        } else {
            permissions.all(::hasPermission)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionSettingsDialog(kind: PermissionKind) {
        AlertDialog.Builder(this)
            .setTitle(getString(kind.titleRes))
            .setMessage(R.string.permissions_manage_access_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                )
            }
            .show()
    }

    private fun finishPermissionFlow() {
        if (fromOnboarding) {
            appSharedPref.firstOnBoarding = false
            Routes.startMainActivity(this)
        }
        finish()
    }

}
