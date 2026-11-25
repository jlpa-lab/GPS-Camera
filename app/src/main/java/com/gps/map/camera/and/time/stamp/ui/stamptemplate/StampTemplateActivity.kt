package com.gps.map.camera.and.time.stamp.ui.stamptemplate

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.AdRemoteConfig
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.ads.banner_all
import com.gps.map.camera.and.time.stamp.utils.location.GpsLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.gps.map.camera.and.time.stamp.utils.Routes

/** Template picker and editor. The camera keeps using the shared stamp renderer. */
class StampTemplateActivity : AppCompatActivity() {
    private val background = 0xFF080D15.toInt()
    private val card = 0xFF172638.toInt()
    private val input = 0xFF101E2B.toInt()
    private val accent = 0xFF7EBCFF.toInt()
    private val sampleAddress = "Banff National Park, Alberta, Canada"
    private var selectedKey = ""
    private var draft = StampCustomization("")
    private var dirty = false
    private var dateMenu: TextView? = null
    private var dateMenuOpen = false
    private var colorPickerSyncing = false
    private var preview: StampPreviewView? = null
    private val listPreviews = mutableListOf<StampPreviewView>()
    private var liveAddress = "Location unavailable"
    private var liveLatitude = Double.NaN
    private var liveLongitude = Double.NaN
    private var liveCapturedAt = System.currentTimeMillis()
    private var liveWeather: WeatherSnapshot? = null
    private val locationProvider by lazy { GpsLocationProvider(this) }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) loadLiveContext()
        else refreshPreviewData()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedKey = StampCustomizationStore.selectedTemplate(this)
        showList(false)
        requestLiveContext()
        loadStampTemplateBanner()
    }

    private fun requestLiveContext() {
        if (locationProvider.hasPermission()) loadLiveContext()
        else locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
    private fun loadStampTemplateBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, findViewById(R.id.fr_banner), isCollapse = false
        )
    }
    private fun loadLiveContext() {
        locationProvider.findCurrentLocation(
            onSuccess = { location ->
                liveLatitude = location.latitude
                liveLongitude = location.longitude
                liveCapturedAt = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                locationProvider.reverseGeocodeFull(location) { address ->
                    liveAddress = address?.takeIf { it.isNotBlank() } ?: "Current location"
                    refreshPreviewData()
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    val weather = OpenMeteoWeather.fetchCached(location.latitude, location.longitude)
                    withContext(Dispatchers.Main) {
                        liveWeather = weather
                        refreshPreviewData()
                    }
                }
            },
            onFailure = { refreshPreviewData() }
        )
    }

    private fun currentRenderData() = StampRenderData(
        address = liveAddress,
        latitude = liveLatitude,
        longitude = liveLongitude,
        capturedAt = liveCapturedAt,
        weather = liveWeather?.stampText
    )

    private fun refreshPreviewData() {
        preview?.setRenderData(currentRenderData())
        listPreviews.forEach { it.setRenderData(currentRenderData()) }
    }

    private fun showList(showToast: Boolean) {
        selectedKey = StampCustomizationStore.selectedTemplate(this)
        listPreviews.clear()
        setContentView(R.layout.activity_stamp_template)
        findViewById<TextView>(R.id.template_title).setText(R.string.tool_template)
        findViewById<TextView>(R.id.template_save).visibility = View.GONE
        findViewById<ImageButton>(R.id.template_back).setOnClickListener { finish() }
        findViewById<View>(R.id.template_list_state).visibility = View.VISIBLE
        findViewById<View>(R.id.template_settings_state).visibility = View.GONE
        val list = findViewById<LinearLayout>(R.id.template_list)
        StampTemplateCatalog.items.forEach { item ->
            val selected = item.key == selectedKey
            val holder = layoutInflater.inflate(R.layout.item_stamp_template, list, false)
            val root = holder.findViewById<LinearLayout>(R.id.template_item_root)
            root.isSelected = selected
            root.setOnClickListener {
                selectedKey = item.key
                StampCustomizationStore.selectTemplate(this@StampTemplateActivity, item.key)
                showSettings()
            }
            holder.findViewById<TextView>(R.id.template_item_title).text = item.name
            val check = holder.findViewById<ImageView>(R.id.template_item_check)
            check.isSelected = selected
            check.setImageResource(if (selected) R.drawable.ic_template_check else android.R.color.transparent)
            val image = StampPreviewView(this).apply {
                setCustomization(StampCustomizationStore.read(this@StampTemplateActivity, item.key).copy(showMapIcon = true, showWeather = true, showCoordinates = true))
                setRenderData(currentRenderData())
            }
            listPreviews += image
            holder.findViewById<FrameLayout>(R.id.template_item_preview_host).addView(image, FrameLayout.LayoutParams(-1, -1))
            list.addView(holder)
        }
        if (showToast) Toast.makeText(this, "Customization saved successfully", Toast.LENGTH_SHORT).show()
    }

    private fun showSettings(loadSaved: Boolean = true) {
        if (loadSaved) {
            draft = StampCustomizationStore.read(this, selectedKey)
            dirty = false
            dateMenuOpen = false
        }
        setContentView(R.layout.activity_stamp_template)
        findViewById<TextView>(R.id.template_title).text = "Template Settings"
        findViewById<TextView>(R.id.template_save).apply {
            visibility = View.VISIBLE
            setOnClickListener { saveSettings() }
        }
        findViewById<ImageButton>(R.id.template_back).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<View>(R.id.template_list_state).visibility = View.GONE
        findViewById<View>(R.id.template_settings_state).visibility = View.VISIBLE
        preview = StampPreviewView(this).apply { setCustomization(draft); setRenderData(currentRenderData()) }
        findViewById<FrameLayout>(R.id.template_preview_host).addView(preview, FrameLayout.LayoutParams(-1, -1))
        bindSettingsViews()
    }

    private fun bindSettingsViews() {
        val auto = findViewById<TextView>(R.id.template_address_auto)
        val manual = findViewById<TextView>(R.id.template_address_manual)
        val detail = listOf<TextView>(
            findViewById(R.id.template_address_none),
            findViewById(R.id.template_address_city),
            findViewById(R.id.template_address_full)
        )
        bindSelection(listOf(auto, manual), if (draft.addressMode == TemplateAddressMode.MANUAL) 1 else 0)
        auto.setOnClickListener { draft = draft.copy(addressMode = TemplateAddressMode.AUTOMATIC); dirty = true; showSettings(false) }
        manual.setOnClickListener {
            draft = draft.copy(
                addressMode = TemplateAddressMode.MANUAL,
                manualAddress = draft.manualAddress.ifBlank { "Banff Avenue, Banff, Alberta" }
            )
            dirty = true
            showSettings(false)
        }
        findViewById<View>(R.id.template_address_detail).visibility = if (draft.addressMode == TemplateAddressMode.AUTOMATIC) View.VISIBLE else View.GONE
        findViewById<View>(R.id.template_manual_address).visibility = if (draft.addressMode == TemplateAddressMode.MANUAL) View.VISIBLE else View.GONE
        bindSelection(detail, draft.addressDetail.ordinal)
        detail.forEachIndexed { index, view -> view.setOnClickListener { draft = draft.copy(addressDetail = TemplateAddressDetail.entries[index]); dirty = true; preview?.setCustomization(draft); bindSelection(detail, index) } }
        if (draft.addressMode == TemplateAddressMode.MANUAL && draft.manualAddress.isBlank()) {
            draft = draft.copy(manualAddress = "Banff Avenue, Banff, Alberta")
            preview?.setCustomization(draft)
        }
        val addressInput = findViewById<EditText>(R.id.template_manual_address_input)
        addressInput.setText(draft.manualAddress)
        addressInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { draft = draft.copy(manualAddress = s.toString()); dirty = true; preview?.setCustomization(draft) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        findViewById<ImageView>(R.id.template_manual_address_map).setOnClickListener { addressInput.setText("Banff Avenue, Banff, Alberta") }

        val dateToggle = findViewById<Switch>(R.id.template_date_toggle)
        dateToggle.isChecked = draft.showDateTime
        dateToggle.setOnCheckedChangeListener { _, checked ->
            draft = draft.copy(showDateTime = checked)
            dirty = true
            dateMenuOpen = false
            preview?.setCustomization(draft)
            bindDateMenu()
        }
        bindDateMenu()

        bindToggle(R.id.template_map_toggle, draft.showMapIcon) { draft = draft.copy(showMapIcon = it); dirty = true; preview?.setCustomization(draft) }
        bindToggle(R.id.template_weather_toggle, draft.showWeather) { draft = draft.copy(showWeather = it); dirty = true; preview?.setCustomization(draft) }
        bindToggle(R.id.template_coordinates_toggle, draft.showCoordinates) { draft = draft.copy(showCoordinates = it); dirty = true; preview?.setCustomization(draft) }

        val colors = draft.recentColors.ifEmpty { StampCustomizationStore.defaultRecentColors }
        findViewById<TextView>(R.id.template_color_add).setOnClickListener { openCustomColorPicker() }
        colors.forEachIndexed { index, value ->
            val swatch = findViewById<TextView>(resources.getIdentifier("template_color_${index + 1}", "id", packageName))
            swatch.background = rounded(value, 12)
            swatch.text = if (draft.backgroundColor == value) "✓" else ""
            swatch.setOnClickListener {
                draft = draft.copy(
                    backgroundColor = value,
                    customColor = null,
                    recentColors = rememberColor(value, draft.recentColors)
                )
                dirty = true
                preview?.setCustomization(draft)
                bindColorSwatches()
            }
        }
        if (draft.customColor != null) {
            val custom = findViewById<TextView>(R.id.template_color_add)
            custom.background = rounded(draft.customColor!!, 12)
            custom.text = if (draft.backgroundColor == (0xCC000000.toInt() or (draft.customColor!! and 0x00FFFFFF))) "✓" else ""
        }

        val positions = listOf<TextView>(findViewById(R.id.template_position_top), findViewById(R.id.template_position_bottom))
        bindSelection(positions, if (draft.position == TemplatePosition.BOTTOM) 1 else 0)
        positions.forEachIndexed { index, view -> view.setOnClickListener { draft = draft.copy(position = if (index == 1) TemplatePosition.BOTTOM else TemplatePosition.TOP); dirty = true; preview?.setCustomization(draft); bindSelection(positions, index) } }
        val alignments = listOf<TextView>(findViewById(R.id.template_alignment_left), findViewById(R.id.template_alignment_center), findViewById(R.id.template_alignment_right))
        bindSelection(alignments, draft.alignment.ordinal)
        alignments.forEachIndexed { index, view -> view.setOnClickListener { draft = draft.copy(alignment = TemplateAlignment.entries[index]); dirty = true; preview?.setCustomization(draft); bindSelection(alignments, index) } }
    }

    private fun bindToggle(id: Int, checked: Boolean, changed: (Boolean) -> Unit) {
        findViewById<Switch>(id).apply { isChecked = checked; setOnCheckedChangeListener { _, value -> changed(value) } }
    }

    private fun bindDateMenu() {
        dateMenu = findViewById(R.id.template_date_format)
        dateMenu?.text = "${StampRenderer.formatDate(System.currentTimeMillis(), draft.dateFormat)}   ⌄"
        dateMenu?.visibility = if (draft.showDateTime) View.VISIBLE else View.GONE
        dateMenu?.setOnClickListener { chooseDateFormat() }

        val optionRoots = listOf<View>(
            findViewById(R.id.template_date_short_month),
            findViewById(R.id.template_date_numeric),
            findViewById(R.id.template_date_time_first),
            findViewById(R.id.template_date_weekday),
            findViewById(R.id.template_date_iso)
        )
        val optionValues = listOf<TextView>(
            findViewById(R.id.template_date_short_month_value),
            findViewById(R.id.template_date_numeric_value),
            findViewById(R.id.template_date_time_first_value),
            findViewById(R.id.template_date_weekday_value),
            findViewById(R.id.template_date_iso_value)
        )
        optionValues.forEachIndexed { index, value ->
            value.text = StampRenderer.formatDate(System.currentTimeMillis(), TemplateDateFormat.entries[index])
            optionRoots[index].isSelected = index == draft.dateFormat.ordinal
            optionRoots[index].setOnClickListener {
                draft = draft.copy(dateFormat = TemplateDateFormat.entries[index])
                dirty = true
                dateMenuOpen = false
                preview?.setCustomization(draft)
                bindDateMenu()
            }
        }
        findViewById<View>(R.id.template_date_menu).visibility =
            if (draft.showDateTime && dateMenuOpen) View.VISIBLE else View.GONE
    }

    private fun bindSelection(views: List<TextView>, selected: Int) {
        views.forEachIndexed { index, view -> view.isSelected = index == selected; view.setTextColor(if (index == selected) Color.WHITE else 0xFFAFBDCE.toInt()) }
    }

    private fun chooseDateFormat() {
        if (!draft.showDateTime) return
        dateMenuOpen = !dateMenuOpen
        bindDateMenu()
    }

    private fun bindColorSwatches() {
        val colors = draft.recentColors.ifEmpty { StampCustomizationStore.defaultRecentColors }
        colors.take(5).forEachIndexed { index, value ->
            val swatch = findViewById<TextView>(resources.getIdentifier("template_color_${index + 1}", "id", packageName))
            swatch.background = rounded(value, 12)
            swatch.text = if (draft.backgroundColor == value) "✓" else ""
        }
    }

    private fun rememberColor(color: Int, existing: List<Int>): List<Int> =
        (listOf(color) + existing.filter { it != color }).take(5)

    private fun openCustomColorPicker() {
        val panel = findViewById<View>(R.id.template_custom_color_panel)
        val colorField = findViewById<TemplateColorField>(R.id.template_color_field)
        val hueField = findViewById<TemplateHueField>(R.id.template_hue_field)
        val hex = findViewById<EditText>(R.id.template_color_hex)
        val startColor = Color.rgb(
            Color.red(draft.backgroundColor),
            Color.green(draft.backgroundColor),
            Color.blue(draft.backgroundColor)
        )
        panel.visibility = View.VISIBLE
        colorPickerSyncing = true
        colorField.setColor(startColor)
        hueField.setHue(colorField.hue())
        hex.setText(formatHex(startColor))
        colorPickerSyncing = false

        fun previewColor(color: Int) {
            preview?.setCustomization(draft.copy(
                backgroundColor = 0xCC000000.toInt() or (color and 0x00FFFFFF),
                customColor = color
            ))
        }

        colorField.onColorChanged = { color ->
            if (!colorPickerSyncing) {
                colorPickerSyncing = true
                hueField.setHue(colorField.hue())
                hex.setText(formatHex(color))
                colorPickerSyncing = false
                previewColor(color)
            }
        }
        hueField.onHueChanged = { hue ->
            if (!colorPickerSyncing) {
                colorPickerSyncing = true
                colorField.setHue(hue, notify = false)
                val color = colorField.color()
                hex.setText(formatHex(color))
                colorPickerSyncing = false
                previewColor(color)
            }
        }
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (colorPickerSyncing) return
                val color = runCatching { Color.parseColor(s.toString()) }.getOrNull() ?: return
                colorPickerSyncing = true
                colorField.setColor(color)
                hueField.setHue(colorField.hue())
                colorPickerSyncing = false
                previewColor(color)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        findViewById<TextView>(R.id.template_color_cancel).setOnClickListener {
            panel.visibility = View.GONE
            preview?.setCustomization(draft)
        }
        findViewById<TextView>(R.id.template_color_apply).setOnClickListener {
            val color = colorField.color()
            draft = draft.copy(
                backgroundColor = 0xCC000000.toInt() or (color and 0x00FFFFFF),
                customColor = color,
                recentColors = rememberColor(0xCC000000.toInt() or (color and 0x00FFFFFF), draft.recentColors)
            )
            dirty = true
            showSettings(loadSaved = false)
        }
    }

    private fun formatHex(color: Int): String = "#%06X".format(color and 0x00FFFFFF)

    private fun saveSettings() {
        StampCustomizationStore.save(this, draft.copy(templateKey = selectedKey))
        dirty = false
        showList(true)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onBackPressed() {
        if (dirty) AlertDialog.Builder(this).setTitle("Discard changes?").setMessage("Your template changes will be lost.").setNegativeButton("Keep editing", null).setPositiveButton("Discard") { _, _ -> showList(false) }.show()
        else super.onBackPressed()
    }

    private class StampPreviewView(context: Context) : View(context) {
        private val bitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.home_gps_camera)
        private var settings = StampCustomization(StampTemplateCatalog.items.first().key)
        private var renderData = StampRenderData("Location unavailable", Double.NaN, Double.NaN)
        init { StampRenderer.configure(context) }
        fun setCustomization(value: StampCustomization) { settings = value; invalidate() }
        fun setRenderData(value: StampRenderData) { renderData = value; invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val source = RectF(0f, 0f, width.toFloat(), height.toFloat())
            val scale = maxOf(source.width() / bitmap.width, source.height() / bitmap.height)
            val w = bitmap.width * scale; val h = bitmap.height * scale
            canvas.drawBitmap(bitmap, null, RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f), Paints.image)
            StampRenderer.draw(canvas, source, settings, renderData)
        }
        private object Paints { val image = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }
    }
}
