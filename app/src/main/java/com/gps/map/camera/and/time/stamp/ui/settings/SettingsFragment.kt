package com.gps.map.camera.and.time.stamp.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import android.app.Dialog
import androidx.fragment.app.Fragment
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.app.AppConstants
import com.gps.map.camera.and.time.stamp.bases.click
import com.gps.map.camera.and.time.stamp.localization.LocaleManager
import com.gps.map.camera.and.time.stamp.pref.AppSharedPreferencesApp
import com.gps.map.camera.and.time.stamp.ui.theme.ThemeManager
import com.gps.map.camera.and.time.stamp.ui.rating.RateAppActivity
import com.gps.map.camera.and.time.stamp.ui.rating.RateAppManager
import com.gps.map.camera.and.time.stamp.ui.permissions.AppPermissionsActivity
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampCustomizationStore
import com.gps.map.camera.and.time.stamp.ui.stamptemplate.StampTemplateCatalog
import com.gps.map.camera.and.time.stamp.utils.Routes

/** Settings for choices that affect the whole app. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private data class LanguageChoice(val code: String, val labelRes: Int)

    private lateinit var preferences: AppSharedPreferencesApp
    private lateinit var state: SettingsState

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
        if (::state.isInitialized) {
            state = readState()
            updateRows(requireView())
        }
    }

    private fun readState(): SettingsState = SettingsState(
        appearance = AppearanceOption.fromKey(preferences.appearanceMode),
        languageCode = LocaleManager.currentLanguage(requireContext()),
        units = MeasurementUnit.fromKey(preferences.measurementUnit),
        stampKey = StampCustomizationStore.selectedTemplate(requireContext())
    )

    private fun bindRows(root: View) {
        bindRow(root, R.id.settings_row_appearance, R.drawable.ic_settings,
            R.string.settings_appearance, appearanceLabel(state.appearance))
        bindRow(root, R.id.settings_row_language, R.drawable.ic_globe,
            R.string.settings_language, languageLabel(state.languageCode))
        bindRow(root, R.id.settings_row_units, R.drawable.ic_layers,
            R.string.settings_units, unitsLabel(state.units))
        bindRow(root, R.id.settings_row_stamp, R.drawable.ic_tool_template,
            R.string.settings_default_stamp, stampLabel(state.stampKey))
        bindRow(root, R.id.settings_row_permissions, R.drawable.ic_location_access,
            R.string.settings_permissions, null)
        bindRow(root, R.id.settings_row_rate, R.drawable.ic_crown,
            R.string.settings_rate_app, null)
        bindRow(root, R.id.settings_row_privacy, R.drawable.ic_location_pin_white,
            R.string.settings_privacy_policy, null)
        bindRow(root, R.id.settings_row_terms, R.drawable.ic_recent_location,
            R.string.settings_terms_of_use, null)

        rowContent(root, R.id.settings_row_appearance).click { showAppearanceDialog() }
        rowContent(root, R.id.settings_row_language).click { openLanguagePage() }
        rowContent(root, R.id.settings_row_units).click { showUnitsDialog() }
        rowContent(root, R.id.settings_row_stamp).click {
            Routes.startStampTemplateActivity(requireActivity())
        }
        rowContent(root, R.id.settings_row_permissions).click { showPermissionScreen() }
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
        updateValue(root, R.id.settings_row_stamp, stampLabel(state.stampKey))
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
        showChoiceDialog(
            title = getString(R.string.settings_choose_appearance),
            labels = options.map(::appearanceLabel),
            selectedIndex = options.indexOf(state.appearance)
        ) { dialog, which ->
            val selected = options[which]
            state = state.copy(appearance = selected)
            dialog.dismiss()
            ThemeManager.apply(requireContext(), selected)
            updateRows(requireView())
        }
    }

    private fun openLanguagePage() {
        Routes.startLanguageActivity(requireActivity(), Bundle().apply {
            putBoolean(AppConstants.KEY_SETTING, true)
        })
    }

    private fun showUnitsDialog() {
        val options = listOf(MeasurementUnit.METRIC, MeasurementUnit.IMPERIAL)
        showChoiceDialog(
            title = getString(R.string.settings_choose_units),
            labels = options.map(::unitsLabel),
            selectedIndex = options.indexOf(state.units)
        ) { dialog, which ->
            val selected = options[which]
            preferences.measurementUnit = selected.key
            state = state.copy(units = selected)
            dialog.dismiss()
            updateRows(requireView())
        }
    }

    private fun showChoiceDialog(
        title: String,
        labels: List<String>,
        selectedIndex: Int,
        onSelected: (Dialog, Int) -> Unit
    ) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_settings_choice, null, false)
        dialog.setContentView(content)
        content.findViewById<TextView>(R.id.settings_choice_title).text = title

        labels.forEachIndexed { index, label ->
            val rowNumber = index + 1
            val rowId = resources.getIdentifier(
                "settings_choice_option_$rowNumber", "id", requireContext().packageName
            )
            val labelId = resources.getIdentifier(
                "settings_choice_option_${rowNumber}_label", "id", requireContext().packageName
            )
            val radioId = resources.getIdentifier(
                "settings_choice_option_${rowNumber}_radio", "id", requireContext().packageName
            )
            val row = content.findViewById<LinearLayout>(rowId)
            row.visibility = View.VISIBLE
            row.findViewById<TextView>(labelId).text = label
            row.findViewById<RadioButton>(radioId).isChecked = index == selectedIndex
            row.setOnClickListener { onSelected(dialog, index) }
        }
        for (index in labels.size + 1..3) {
            val rowId = resources.getIdentifier(
                "settings_choice_option_$index", "id", requireContext().packageName
            )
            content.findViewById<View>(rowId).visibility = View.GONE
        }
        content.findViewById<View>(R.id.settings_choice_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            configureDialogWindow(dialog)
        }
        dialog.show()
        configureDialogWindow(dialog)
    }

    private fun configureDialogWindow(dialog: Dialog) {
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.58f)
            setLayout(dp(320), WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun showPermissionScreen() {
        startActivity(Intent(requireContext(), AppPermissionsActivity::class.java))
    }

    private fun rateApp() {
        val context = context ?: return
        startActivity(Intent(context, RateAppActivity::class.java).apply {
            putExtra(
                RateAppActivity.EXTRA_ENTRY_POINT,
                RateAppManager.EntryPoint.SETTINGS.name
            )
        })
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

    private fun stampLabel(value: String): String = StampTemplateCatalog.item(value).name

    private fun languageLabel(code: String): String {
        val option = languageChoices().firstOrNull { it.code == code }
        return option?.let { getString(it.labelRes).substringBefore('\n') } ?: code
    }

    private fun appVersion(): String = runCatching {
        @Suppress("DEPRECATION")
        requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
            ?: ""
    }.getOrDefault("")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
