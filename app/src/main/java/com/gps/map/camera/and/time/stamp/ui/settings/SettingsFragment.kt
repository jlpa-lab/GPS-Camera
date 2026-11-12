package com.gps.map.camera.and.time.stamp.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.play.core.review.ReviewManagerFactory
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.app.AppConstants
import com.gps.map.camera.and.time.stamp.bases.click
import com.gps.map.camera.and.time.stamp.localization.LocaleManager
import com.gps.map.camera.and.time.stamp.pref.AppSharedPreferencesApp
import com.gps.map.camera.and.time.stamp.ui.theme.ThemeManager

/** Settings for choices that affect the whole app. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private data class LanguageChoice(val code: String, val labelRes: Int)

    private data class PermissionChoice(val labelRes: Int, val permission: String)

    private lateinit var preferences: AppSharedPreferencesApp
    private lateinit var state: SettingsState

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        showPermissionDialog()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = AppSharedPreferencesApp(requireContext().applicationContext)
        state = readState()

        bindRows(view)
        view.findViewById<TextView>(R.id.settings_version).text = getString(
            R.string.settings_version,
            appVersion()
        )
    }

    override fun onResume() {
        super.onResume()
        if (::state.isInitialized) updateRows(requireView())
    }

    private fun readState(): SettingsState = SettingsState(
        appearance = AppearanceOption.fromKey(preferences.appearanceMode),
        languageCode = LocaleManager.currentLanguage(requireContext()),
        units = MeasurementUnit.fromKey(preferences.measurementUnit),
        stamp = StampTemplate.fromKey(preferences.defaultStamp)
    )

    private fun bindRows(root: View) {
        bindRow(root, R.id.settings_row_appearance, R.drawable.ic_settings,
            R.string.settings_appearance, appearanceLabel(state.appearance))
        bindRow(root, R.id.settings_row_language, R.drawable.ic_globe,
            R.string.settings_language, languageLabel(state.languageCode))
        bindRow(root, R.id.settings_row_units, R.drawable.ic_layers,
            R.string.settings_units, unitsLabel(state.units))
        bindRow(root, R.id.settings_row_stamp, R.drawable.ic_tool_template,
            R.string.settings_default_stamp, stampLabel(state.stamp))
        bindRow(root, R.id.settings_row_permissions, R.drawable.ic_location_access,
            R.string.settings_permissions, null)
        bindRow(root, R.id.settings_row_rate, R.drawable.ic_crown,
            R.string.settings_rate_app, null)
        bindRow(root, R.id.settings_row_privacy, R.drawable.ic_location_pin_white,
            R.string.settings_privacy_policy, null)
        bindRow(root, R.id.settings_row_terms, R.drawable.ic_recent_location,
            R.string.settings_terms_of_use, null)

        rowContent(root, R.id.settings_row_appearance).click { showAppearanceDialog() }
        rowContent(root, R.id.settings_row_language).click { showLanguageDialog() }
        rowContent(root, R.id.settings_row_units).click { showUnitsDialog() }
        rowContent(root, R.id.settings_row_stamp).click { showStampDialog() }
        rowContent(root, R.id.settings_row_permissions).click { showPermissionDialog() }
        rowContent(root, R.id.settings_row_rate).click { rateApp() }
        rowContent(root, R.id.settings_row_privacy).click {
            openLink(AppConstants.LINK_PRIVACY_POLICY)
        }
        rowContent(root, R.id.settings_row_terms).click {
            openLink(AppConstants.LINK_TERMS_AND_CONDITIONS)
        }
    }

    private fun updateRows(root: View) {
        updateValue(root, R.id.settings_row_appearance, appearanceLabel(state.appearance))
        updateValue(root, R.id.settings_row_language, languageLabel(state.languageCode))
        updateValue(root, R.id.settings_row_units, unitsLabel(state.units))
        updateValue(root, R.id.settings_row_stamp, stampLabel(state.stamp))
    }

    private fun bindRow(root: View, rowId: Int, icon: Int, title: Int, value: String?) {
        val row = root.findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.settings_row_icon).setImageResource(icon)
        row.findViewById<TextView>(R.id.settings_row_title).setText(title)
        updateValue(row, value)
    }

    private fun updateValue(root: View, rowId: Int, value: String) {
        updateValue(root.findViewById(rowId), value)
    }

    private fun updateValue(row: View, value: String?) {
        row.findViewById<TextView>(R.id.settings_row_value).apply {
            text = value
            visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun rowContent(root: View, rowId: Int): View =
        root.findViewById<View>(rowId).findViewById(R.id.settings_row_content)

    private fun showAppearanceDialog() {
        val options = listOf(AppearanceOption.SYSTEM, AppearanceOption.LIGHT, AppearanceOption.DARK)
        val labels = options.map { appearanceLabel(it) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_choose_appearance)
            .setSingleChoiceItems(labels, options.indexOf(state.appearance)) { dialog, which ->
                val selected = options[which]
                state = state.copy(appearance = selected)
                ThemeManager.apply(requireContext(), selected)
                dialog.dismiss()
                updateRows(requireView())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val choices = languageChoices()
        val labels = choices.map { languageLabel(it.code) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_choose_language)
            .setSingleChoiceItems(labels, choices.indexOfFirst { it.code == state.languageCode }) { dialog, which ->
                val selected = choices[which]
                preferences.languageCode = selected.code
                LocaleManager.apply(requireContext(), selected.code)
                state = state.copy(languageCode = selected.code)
                dialog.dismiss()
                requireActivity().recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showUnitsDialog() {
        val options = listOf(MeasurementUnit.METRIC, MeasurementUnit.IMPERIAL)
        val labels = options.map { unitsLabel(it) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_choose_units)
            .setSingleChoiceItems(labels, options.indexOf(state.units)) { dialog, which ->
                val selected = options[which]
                preferences.measurementUnit = selected.key
                state = state.copy(units = selected)
                dialog.dismiss()
                updateRows(requireView())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showStampDialog() {
        val options = listOf(StampTemplate.CLASSIC, StampTemplate.MINIMAL, StampTemplate.DETAILED)
        val labels = options.map { stampLabel(it) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_choose_stamp)
            .setSingleChoiceItems(labels, options.indexOf(state.stamp)) { dialog, which ->
                val selected = options[which]
                preferences.defaultStamp = selected.key
                state = state.copy(stamp = selected)
                dialog.dismiss()
                updateRows(requireView())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPermissionDialog() {
        if (!isAdded) return
        val choices = permissionChoices()
        val labels = choices.map { choice ->
            val status = if (hasPermission(choice.permission)) {
                getString(R.string.settings_permission_granted)
            } else {
                getString(R.string.settings_permission_denied)
            }
            "${getString(choice.labelRes)}    $status"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_permissions_title)
            .setItems(labels) { _, which -> requestPermission(choices[which].permission) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestPermission(permission: String) {
        if (hasPermission(permission)) return

        val requestedKey = "settings_permission_requested_$permission"
        val requestedBefore = preferences.sharedPref.getBoolean(requestedKey, false)
        val shouldExplain = ActivityCompat.shouldShowRequestPermissionRationale(
            requireActivity(), permission
        )
        if (requestedBefore && !shouldExplain) {
            openAppSettings()
            return
        }

        preferences.sharedPref.edit().putBoolean(requestedKey, true).apply()
        permissionLauncher.launch(arrayOf(permission))
    }

    private fun rateApp() {
        val activity = activity ?: return
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { result ->
            if (result.isSuccessful) {
                manager.launchReviewFlow(activity, result.result)
                    .addOnCompleteListener { openPlayStore() }
            } else {
                openPlayStore()
            }
        }
    }

    private fun openPlayStore() {
        val packageId = requireContext().packageName
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageId"))
        try {
            startActivity(marketIntent)
        } catch (_: Exception) {
            openLink("https://play.google.com/store/apps/details?id=$packageId")
        }
    }

    private fun openLink(url: String) {
        if (url.isBlank()) {
            Toast.makeText(requireContext(), R.string.settings_link_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.settings_link_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppSettings() {
        val packageId = requireContext().packageName
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageId")
            )
        )
    }

    private fun permissionChoices(): List<PermissionChoice> {
        val choices = mutableListOf(
            PermissionChoice(R.string.settings_permission_location, Manifest.permission.ACCESS_FINE_LOCATION),
            PermissionChoice(R.string.settings_permission_camera, Manifest.permission.CAMERA)
        )
        choices += PermissionChoice(
            R.string.settings_permission_photos,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            choices += PermissionChoice(
                R.string.settings_permission_notifications,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        return choices
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    private fun languageChoices(): List<LanguageChoice> = listOf(
        LanguageChoice("en", R.string.language_english),
        LanguageChoice("vi", R.string.language_vietnamese),
        LanguageChoice("ja", R.string.language_japanese),
        LanguageChoice("ko", R.string.language_korean),
        LanguageChoice("fr", R.string.language_french),
        LanguageChoice("es", R.string.language_spanish),
        LanguageChoice("de", R.string.language_german),
        LanguageChoice("pt", R.string.language_portuguese),
        LanguageChoice("hi", R.string.language_hindi),
        LanguageChoice("zh", R.string.language_chinese),
        LanguageChoice("ar", R.string.language_arabic),
        LanguageChoice("ru", R.string.language_russian)
    )

    private fun appearanceLabel(value: AppearanceOption): String = getString(
        when (value) {
            AppearanceOption.SYSTEM -> R.string.settings_system
            AppearanceOption.LIGHT -> R.string.settings_light
            AppearanceOption.DARK -> R.string.settings_dark
        }
    )

    private fun unitsLabel(value: MeasurementUnit): String = getString(
        if (value == MeasurementUnit.METRIC) R.string.settings_metric else R.string.settings_imperial
    )

    private fun stampLabel(value: StampTemplate): String = getString(
        when (value) {
            StampTemplate.CLASSIC -> R.string.settings_classic
            StampTemplate.MINIMAL -> R.string.settings_minimal
            StampTemplate.DETAILED -> R.string.settings_detailed
        }
    )

    private fun languageLabel(code: String): String {
        val option = languageChoices().firstOrNull { it.code == code }
        return option?.let { getString(it.labelRes).substringBefore('\n') } ?: code
    }

    private fun appVersion(): String = runCatching {
        @Suppress("DEPRECATION")
        requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
            ?: ""
    }.getOrDefault("")
}
